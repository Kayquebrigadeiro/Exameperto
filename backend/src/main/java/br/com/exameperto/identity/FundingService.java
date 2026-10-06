package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
class FundingService {
    private final JdbcTemplate jdbc;
    private final DelivererService evidence;
    private final MfaService mfa;

    FundingService(JdbcTemplate jdbc, DelivererService evidence, MfaService mfa) {
        this.jdbc = jdbc; this.evidence = evidence; this.mfa = mfa;
    }

    @Transactional
    DocumentView uploadEvidence(UUID actor, MultipartFile file) {
        evidence.active(actor);
        DelivererService.StoredEvidence stored = evidence.storeEvidence(actor, "FINANCIAMENTO", file);
        return evidence.document(stored.id());
    }

    @Transactional
    FundingView record(AuthService.SessionPrincipal principal, UUID programId, FundingInput input, String idem) {
        UUID actor = principal.userId(); evidence.active(actor); mfa.requireVerified(principal);
        UUID institution = institutionForProgram(programId); requireManager(actor, institution);
        if (idem == null || idem.isBlank()) throw error(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_REQUIRED", "A operação financeira exige Idempotency-Key.");
        byte[] hash = hash(input.amount().setScale(2).toPlainString()+"|"+input.currency()+"|"+input.evidenceDocumentId()+"|"+input.sourceReference());
        FundingView replay = replay(actor, "RECORD:"+programId, idem, hash); if (replay != null) return replay;
        requireEvidence(input.evidenceDocumentId(), actor, false);
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO aporte(id,programa_id,registrado_por,comprovante_id,valor,moeda,origem_referencia,estado) VALUES (?,?,?,?,?,?,?,'PENDENTE')",
                id, programId, actor, input.evidenceDocumentId(), input.amount(), input.currency().name(), input.sourceReference());
            jdbc.update("INSERT INTO financiamento_idempotencia(ator_id,operacao,chave,request_hash,aporte_id) VALUES (?,?,?,?,?)", actor,"RECORD:"+programId,idem,hash,id);
        } catch (DataIntegrityViolationException ex) {
            FundingView existing = replay(actor, "RECORD:"+programId, idem, hash); if (existing != null) return existing;
            throw error(HttpStatus.CONFLICT, "FUNDING_CONFLICT", "A referência do aporte já foi registrada.");
        }
        audit(actor,id,"REGISTRADO",null); return funding(id);
    }

    List<FundingView> list(AuthService.SessionPrincipal principal, UUID programId) { UUID actor=principal.userId(); evidence.active(actor); mfa.requireVerified(principal); UUID institution=institutionForProgram(programId); requireManager(actor,institution); return jdbc.query("SELECT id FROM aporte WHERE programa_id=? ORDER BY created_at,id",(rs,n)->funding(rs.getObject(1,UUID.class)),programId); }

    BalanceView balance(AuthService.SessionPrincipal principal, UUID programId) {
        UUID actor=principal.userId(); evidence.active(actor); mfa.requireVerified(principal);
        UUID institution=institutionForProgram(programId); requireManager(actor,institution);
        Map<String,Object> row=jdbc.queryForMap("SELECT COALESCE(SUM(valor),0)::numeric(19,2) AS disponivel, count(*) AS versao FROM lancamento_aporte WHERE programa_id=? AND moeda='BRL'",programId);
        BigDecimal available=(BigDecimal)row.get("disponivel"); long version=((Number)row.get("versao")).longValue();
        return new BalanceView(programId,"BRL",available,BigDecimal.ZERO,BigDecimal.ZERO,version);
    }

    @Transactional
    FundingView review(AuthService.SessionPrincipal principal, UUID fundingId, FundingReviewInput input, long expectedVersion, String idem) {
        UUID actor=principal.userId(); evidence.active(actor); mfa.requireVerified(principal);
        Map<String,Object> row=fundingForUpdate(fundingId); UUID institution=institutionForProgram((UUID)row.get("programa_id")); requireManager(actor,institution);
        if (idem == null || idem.isBlank()) throw error(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_REQUIRED","A revisão financeira exige Idempotency-Key.");
        byte[] hash=hash(input.decision()+"|"+input.reconciliationEvidenceId()+"|"+input.reasonCode()+"|"+expectedVersion);
        FundingView replay=replay(actor,"REVIEW:"+fundingId,idem,hash); if(replay!=null)return replay;
        long version=((Number)row.get("version")).longValue();
        if(version!=expectedVersion) throw error(HttpStatus.PRECONDITION_FAILED,"VERSION_MISMATCH","O aporte mudou; recarregue antes de revisar.");
        if(!"PENDENTE".equals(row.get("estado"))) throw error(HttpStatus.CONFLICT,"REVIEW_CONFLICT","O aporte já possui decisão.");
        if(actor.equals(row.get("registrado_por"))) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","O registrador não pode confirmar o próprio aporte.");
        requireEvidence(input.reconciliationEvidenceId(),actor,true);
        String state=input.decision().name();
        jdbc.update("UPDATE aporte SET estado=?,revisado_por=?,revisado_em=clock_timestamp(),conciliacao_documento_id=?,revisao_motivo_codigo=?,version=version+1,updated_at=clock_timestamp() WHERE id=? AND version=?",state,actor,input.reconciliationEvidenceId(),input.reasonCode(),fundingId,expectedVersion);
        if(input.decision()==FundingReviewInput.Decision.CONFIRMADO) {
            try { jdbc.update("INSERT INTO lancamento_aporte(id,aporte_id,programa_id,valor,moeda) VALUES (?,?,?,?,?)",UUID.randomUUID(),fundingId,row.get("programa_id"),row.get("valor"),row.get("moeda")); }
            catch(DataIntegrityViolationException ex) { throw error(HttpStatus.CONFLICT,"FUNDING_CONFLICT","O crédito do aporte já foi lançado."); }
        }
        jdbc.update("INSERT INTO financiamento_idempotencia(ator_id,operacao,chave,request_hash,aporte_id) VALUES (?,?,?,?,?)",actor,"REVIEW:"+fundingId,idem,hash,fundingId);
        audit(actor,fundingId,state,input.reasonCode()); return funding(fundingId);
    }

    @Transactional
    DocumentView inspectReconciliation(AuthService.SessionPrincipal principal, UUID fundingId, UUID documentId) {
        UUID actor=principal.userId(); evidence.active(actor); mfa.requireVerified(principal);
        Map<String,Object> row=fundingForUpdate(fundingId); requireManager(actor,institutionForProgram((UUID)row.get("programa_id")));
        if(!"PENDENTE".equals(row.get("estado"))) throw error(HttpStatus.CONFLICT,"REVIEW_CONFLICT","O aporte já possui decisão.");
        requireEvidence(documentId,actor,false); evidence.inspectEvidence(documentId); return evidence.document(documentId);
    }

    private FundingView replay(UUID actor,String operation,String key,byte[] hash) {
        try { Map<String,Object> row=jdbc.queryForMap("SELECT request_hash,aporte_id FROM financiamento_idempotencia WHERE ator_id=? AND operacao=? AND chave=?",actor,operation,key); if(!MessageDigest.isEqual((byte[])row.get("request_hash"),hash)) throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","A mesma chave foi usada com dados diferentes."); return funding((UUID)row.get("aporte_id")); }
        catch(EmptyResultDataAccessException ex){return null;}
    }
    private void requireEvidence(UUID id,UUID owner,boolean inspected) { Map<String,Object> d; try{d=jdbc.queryForMap("SELECT proprietario_id,categoria,estado FROM documento WHERE id=?",id);}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Evidência financeira não encontrada.");} if(!owner.equals(d.get("proprietario_id"))||!List.of("FINANCIAMENTO","COMPROVANTE").contains(d.get("categoria"))||"REJEITADO".equals(d.get("estado"))||"EXPURGADO".equals(d.get("estado"))||(inspected&&!"INSPECAO_APROVADA".equals(d.get("estado"))))throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Evidência financeira não autorizada."); }
    private UUID institutionForProgram(UUID program) { try{return jdbc.queryForObject("SELECT instituicao_id FROM programa WHERE id=? AND estado<>'SUSPENSO'",UUID.class,program);}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Programa não encontrado.");} }
    private void requireManager(UUID actor,UUID institution) { if(jdbc.queryForObject("SELECT count(*) FROM membro_instituicao WHERE instituicao_id=? AND usuario_id=? AND papel='GESTOR_FINANCEIRO' AND revogado_em IS NULL",Long.class,institution,actor)!=1 || jdbc.queryForObject("SELECT count(*) FROM papel_global WHERE usuario_id=? AND papel='GESTOR_FINANCEIRO' AND revogado_em IS NULL",Long.class,actor)!=1) throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","Gestor financeiro nominal não autorizado."); }
    private Map<String,Object> fundingForUpdate(UUID id){try{return jdbc.queryForMap("SELECT * FROM aporte WHERE id=? FOR UPDATE",id);}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Aporte não encontrado.");}}
    private FundingView funding(UUID id){return jdbc.queryForObject("SELECT id,programa_id,valor,moeda,estado,comprovante_id,conciliacao_documento_id,revisado_em,revisao_motivo_codigo,version FROM aporte WHERE id=?",(rs,n)->new FundingView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getBigDecimal(3),rs.getString(4),rs.getString(5),rs.getObject(6,UUID.class),rs.getObject(7,UUID.class),rs.getTimestamp(8)==null?null:rs.getTimestamp(8).toInstant(),rs.getString(9),rs.getLong(10)),id);}
    private void audit(UUID actor,UUID funding,String action,String reason){jdbc.update("INSERT INTO auditoria_financeira(id,ator_id,aporte_id,acao,motivo_codigo) VALUES (?,?,?,?,?)",UUID.randomUUID(),actor,funding,action,reason);}
    private byte[] hash(String value){try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}catch(Exception ex){throw new IllegalStateException(ex);}}
    static FundingException error(HttpStatus s,String c,String m){return new FundingException(s,c,m);}
    static final class FundingException extends org.springframework.web.server.ResponseStatusException { private final ApiError body; FundingException(HttpStatus s,String c,String m){super(s,m);body=new ApiError(c,m,UUID.randomUUID(),s.is5xxServerError());} ApiError body(){return body;} }
}
