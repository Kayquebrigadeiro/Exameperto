package br.com.exameperto.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
class BenefitService {
    private final JdbcTemplate jdbc; private final DelivererService evidence; private final MfaService mfa; private final ObjectMapper json;
    BenefitService(JdbcTemplate jdbc, DelivererService evidence, MfaService mfa, ObjectMapper json){this.jdbc=jdbc;this.evidence=evidence;this.mfa=mfa;this.json=json;}

    @Transactional
    BenefitRequestView create(UUID actor, BenefitRequestInput input) {
        evidence.active(actor); authorize(actor,input.patientId());
        Map<String,Object> policy=activePolicy(input.policyId());
        UUID id=UUID.randomUUID(); String snapshot=String.valueOf(policy.get("criterios"));
        try {
            jdbc.update("INSERT INTO solicitacao_beneficio(id,paciente_id,solicitante_id,instituicao_id,politica_id,politica_versao,politica_snapshot,estado) VALUES (?,?,?,?,?,?,?::jsonb,'EM_ANALISE')",
                id,input.patientId(),actor,policy.get("instituicao_id"),input.policyId(),policy.get("versao"),snapshot);
            for(BenefitDimension d:input.dimensions()) jdbc.update("INSERT INTO solicitacao_dimensao(solicitacao_id,dimensao) VALUES (?,?)",id,d.name());
            createReview(id,"INICIAL",0);
        } catch(DataIntegrityViolationException ex){throw error(HttpStatus.CONFLICT,"BENEFIT_CONFLICT","Não foi possível criar a solicitação.");}
        return request(actor,id);
    }

    BenefitRequestView get(UUID actor, UUID id){authorizeRead(actor,id);return request(actor,id);}

    List<BenefitRequestView> list(UUID actor){
        evidence.active(actor);
        List<UUID> ids=jdbc.query("SELECT DISTINCT s.id FROM solicitacao_beneficio s LEFT JOIN membro_instituicao m ON m.instituicao_id=s.instituicao_id AND m.usuario_id=? AND m.revogado_em IS NULL WHERE s.paciente_id IN (SELECT id FROM paciente WHERE usuario_id=?) OR EXISTS (SELECT 1 FROM autorizacao_paciente a JOIN autorizacao_escopo e ON e.autorizacao_id=a.id WHERE a.paciente_id=s.paciente_id AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() AND e.escopo='BENEFICIOS') OR m.usuario_id=? ORDER BY s.created_at DESC,s.id",(rs,n)->rs.getObject(1,UUID.class),actor,actor,actor,actor);
        return ids.stream().map(id->request(actor,id)).toList();
    }

    @Transactional
    BenefitRequestView upload(UUID actor,UUID id,long version,BenefitDimension purpose,MultipartFile file){
        authorizeWrite(actor,id); Map<String,Object> row=requestForUpdate(id);requireVersion(row,version);requireDimension(id,purpose);
        DelivererService.StoredEvidence stored=evidence.storeEvidence(actor,purpose.name(),file);
        try{attach(id,stored.id(),purpose);return request(actor,id);}catch(RuntimeException ex){evidence.discardEvidence(stored);throw ex;}
    }

    @Transactional
    BenefitRequestView reuse(UUID actor,UUID id,BenefitDocumentReuseInput input){
        authorizeWrite(actor,id);Map<String,Object> row=requestForUpdate(id);requireVersion(row,input.requestVersion());requireDimension(id,input.purpose());
        long owned=jdbc.queryForObject("SELECT count(*) FROM documento WHERE id=? AND proprietario_id=? AND categoria=? AND estado NOT IN ('REJEITADO','EXPURGADO')",Long.class,input.documentId(),actor,input.purpose().name());
        if(owned!=1)throw notFound();attach(id,input.documentId(),input.purpose());return request(actor,id);
    }

    @Transactional
    BenefitRequestView appeal(UUID actor,UUID id,BenefitAppealInput input){
        authorizeWrite(actor,id);Map<String,Object> row=requestForUpdate(id);requireVersion(row,input.requestVersion());
        if(!"DECIDIDA".equals(row.get("estado")))throw error(HttpStatus.CONFLICT,"APPEAL_NOT_AVAILABLE","Recurso só pode ser aberto após uma decisão.");
        jdbc.update("UPDATE solicitacao_beneficio SET estado='RECURSO',version=version+1,updated_at=clock_timestamp() WHERE id=? AND version=?",id,input.requestVersion());
        createReview(id,"RECURSO",input.requestVersion()+1);return request(actor,id);
    }

    List<BenefitReviewView> queue(UUID analyst){
        evidence.active(analyst);requireAnalyst(analyst);
        return jdbc.query("SELECT r.id FROM revisao_beneficio r JOIN solicitacao_beneficio s ON s.id=r.solicitacao_id JOIN membro_instituicao m ON m.instituicao_id=s.instituicao_id WHERE m.usuario_id=? AND m.papel='ANALISTA_BENEFICIO' AND m.revogado_em IS NULL AND r.estado IN ('PENDENTE','ATRIBUIDA') ORDER BY r.criado_em,r.id",(rs,n)->review(rs.getObject(1,UUID.class)),analyst).stream().toList();
    }

    @Transactional
    BenefitReviewView assign(AuthService.SessionPrincipal principal,UUID id){
        UUID analyst=principal.userId();evidence.requireAnalyst(analyst);mfa.requireVerified(principal);Map<String,Object> row=reviewForUpdate(id);institutionMember(analyst,(UUID)row.get("solicitacao_id"));
        if(!"PENDENTE".equals(row.get("estado")))throw error(HttpStatus.CONFLICT,"REVIEW_CONFLICT","A revisão não está pendente.");
        UUID owner=jdbc.queryForObject("SELECT p.usuario_id FROM solicitacao_beneficio s JOIN paciente p ON p.id=s.paciente_id WHERE s.id=?",UUID.class,row.get("solicitacao_id"));
        UUID requester=jdbc.queryForObject("SELECT solicitante_id FROM solicitacao_beneficio WHERE id=?",UUID.class,row.get("solicitacao_id"));
        if(analyst.equals(owner)||analyst.equals(requester)||("RECURSO".equals(row.get("tipo"))&&jdbc.queryForObject("SELECT count(*) FROM revisao_beneficio WHERE solicitacao_id=? AND analista_id=? AND estado='CONCLUIDA'",Long.class,row.get("solicitacao_id"),analyst)>0))throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","O analista não pode revisar este próprio caso ou recurso.");
        jdbc.update("UPDATE revisao_beneficio SET analista_id=?,estado='ATRIBUIDA',atribuido_em=clock_timestamp() WHERE id=?",analyst,id);return review(id);
    }

    @Transactional
    BenefitReviewView inspect(AuthService.SessionPrincipal principal,UUID reviewId,UUID docId){
        Map<String,Object> row=assigned(principal,reviewId);long member=jdbc.queryForObject("SELECT count(*) FROM beneficio_evidencia be JOIN revisao_beneficio r ON r.solicitacao_id=be.solicitacao_id WHERE r.id=? AND be.documento_id=? AND be.substituido_em IS NULL",Long.class,reviewId,docId);
        if(member!=1)throw notFound();boolean safe=evidence.inspectEvidence(docId);if(!safe)jdbc.update("UPDATE revisao_beneficio SET estado='BLOQUEADA',motivo_codigo='ARQUIVO_ESTRUTURALMENTE_INSEGURO' WHERE id=?",reviewId);return review(reviewId);
    }

    @Transactional
    BenefitReviewView decide(AuthService.SessionPrincipal principal,UUID reviewId,BenefitDecisionInput input){
        Map<String,Object> row=assigned(principal,reviewId);Map<String,Object> req=requestForUpdate((UUID)row.get("solicitacao_id"));
        if(((Number)row.get("solicitacao_version")).longValue()!=((Number)req.get("version")).longValue())throw error(HttpStatus.CONFLICT,"STALE_REVIEW","A revisão ficou desatualizada.");
        Map<String,Object> policy=activePolicy((UUID)req.get("politica_id"));BigDecimal pct=input.decision()==BenefitDecisionInput.Decision.REJEITADA?BigDecimal.ZERO:BenefitCalculator.percentage(input.percentage(),(BigDecimal)policy.get("percentual_maximo"));
        long pending=jdbc.queryForObject("SELECT count(*) FROM beneficio_evidencia be JOIN revisao_beneficio r ON r.solicitacao_id=be.solicitacao_id JOIN documento d ON d.id=be.documento_id WHERE r.id=? AND be.substituido_em IS NULL AND d.estado<>'INSPECAO_APROVADA'",Long.class,reviewId);
        if(pending>0)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INSPECTION_REQUIRED","Toda evidência precisa concluir a inspeção estrutural.");
        jdbc.update("UPDATE revisao_beneficio SET estado='CONCLUIDA',decisao=?,percentual=?,motivo_codigo=?,decidido_em=clock_timestamp() WHERE id=?",input.decision().name(),pct,input.reason(),reviewId);
        jdbc.update("UPDATE solicitacao_beneficio SET estado='DECIDIDA',decisao=?,percentual=?,motivo_codigo=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",input.decision().name(),pct,input.reason(),req.get("id"));
        return review(reviewId);
    }

    private void attach(UUID request,UUID doc,BenefitDimension purpose){jdbc.update("UPDATE beneficio_evidencia SET substituido_em=clock_timestamp() WHERE solicitacao_id=? AND finalidade=? AND substituido_em IS NULL",request,purpose.name());try{jdbc.update("INSERT INTO beneficio_evidencia(solicitacao_id,documento_id,finalidade) VALUES (?,?,?)",request,doc,purpose.name());}catch(DataIntegrityViolationException ex){throw error(HttpStatus.CONFLICT,"DOCUMENT_CONFLICT","O documento já está vinculado a esta solicitação.");}jdbc.update("UPDATE solicitacao_beneficio SET estado='EM_ANALISE',version=version+1,updated_at=clock_timestamp() WHERE id=?",request);jdbc.update("UPDATE revisao_beneficio SET estado='BLOQUEADA',motivo_codigo='EVIDENCIA_SUBSTITUIDA' WHERE solicitacao_id=? AND estado IN ('PENDENTE','ATRIBUIDA')",request);long version=jdbc.queryForObject("SELECT version FROM solicitacao_beneficio WHERE id=?",Long.class,request);createReview(request,"INICIAL",version);}
    private void createReview(UUID request,String type,long version){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO revisao_beneficio(id,solicitacao_id,tipo,solicitacao_version,estado) VALUES (?,?,?,?,'PENDENTE')",id,request,type,version);}
    private BenefitRequestView request(UUID actor,UUID id){Map<String,Object> r=requestRow(id);return new BenefitRequestView(id,(UUID)r.get("paciente_id"),(UUID)r.get("instituicao_id"),(UUID)r.get("politica_id"),((Number)r.get("politica_versao")).intValue(),(String)r.get("estado"),dimensions(id),(String)r.get("decisao"),(BigDecimal)r.get("percentual"),(String)r.get("motivo_codigo"),((Number)r.get("version")).longValue(),documents(id),reviews(id));}
    private Set<BenefitDimension> dimensions(UUID id){return new LinkedHashSet<>(jdbc.queryForList("SELECT dimensao FROM solicitacao_dimensao WHERE solicitacao_id=? ORDER BY dimensao",String.class,id).stream().map(BenefitDimension::valueOf).toList());}
    private List<BenefitEvidenceView> documents(UUID id){return jdbc.query("SELECT d.id,be.finalidade,d.mime,d.tamanho,d.estado,be.substituido_em IS NULL FROM beneficio_evidencia be JOIN documento d ON d.id=be.documento_id WHERE be.solicitacao_id=? ORDER BY be.criado_em,d.id",(rs,n)->new BenefitEvidenceView(rs.getObject(1,UUID.class),BenefitDimension.valueOf(rs.getString(2)),rs.getString(3),rs.getLong(4),rs.getString(5),rs.getBoolean(6)),id);}
    private List<BenefitReviewView> reviews(UUID id){return jdbc.query("SELECT id,solicitacao_id,tipo,solicitacao_version,analista_id,estado,decisao,percentual,motivo_codigo FROM revisao_beneficio WHERE solicitacao_id=? ORDER BY criado_em,id",(rs,n)->new BenefitReviewView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getLong(4),rs.getObject(5,UUID.class),rs.getString(6),rs.getString(7),(BigDecimal)rs.getObject(8),rs.getString(9)),id);}
    private BenefitReviewView review(UUID id){try{return jdbc.queryForObject("SELECT id,solicitacao_id,tipo,solicitacao_version,analista_id,estado,decisao,percentual,motivo_codigo FROM revisao_beneficio WHERE id=?",(rs,n)->new BenefitReviewView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getLong(4),rs.getObject(5,UUID.class),rs.getString(6),rs.getString(7),(BigDecimal)rs.getObject(8),rs.getString(9)),id);}catch(EmptyResultDataAccessException ex){throw notFound();}}
    private Map<String,Object> reviewForUpdate(UUID id){try{return jdbc.queryForMap("SELECT * FROM revisao_beneficio WHERE id=? FOR UPDATE",id);}catch(EmptyResultDataAccessException ex){throw notFound();}}
    private Map<String,Object> assigned(AuthService.SessionPrincipal p,UUID id){evidence.requireAnalyst(p.userId());mfa.requireVerified(p);Map<String,Object> r=reviewForUpdate(id);if(!p.userId().equals(r.get("analista_id"))||!"ATRIBUIDA".equals(r.get("estado")))throw notFound();return r;}
    private void authorize(UUID actor,UUID patient){if(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE id=? AND usuario_id=?",Long.class,patient,actor)==1)return;long grant=jdbc.queryForObject("SELECT count(*) FROM autorizacao_paciente a JOIN autorizacao_escopo e ON e.autorizacao_id=a.id WHERE a.paciente_id=? AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() AND e.escopo='BENEFICIOS'",Long.class,patient,actor);if(grant!=1)throw notFound();}
    private void authorizeRead(UUID actor,UUID id){if(jdbc.queryForObject("SELECT count(*) FROM solicitacao_beneficio s JOIN paciente p ON p.id=s.paciente_id WHERE s.id=? AND p.usuario_id=?",Long.class,id,actor)==1)return;Map<String,Object> r=requestRow(id);long grant=jdbc.queryForObject("SELECT count(*) FROM autorizacao_paciente a JOIN autorizacao_escopo e ON e.autorizacao_id=a.id WHERE a.paciente_id=? AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() AND e.escopo='BENEFICIOS'",Long.class,r.get("paciente_id"),actor);if(grant==1)return;if(jdbc.queryForObject("SELECT count(*) FROM revisao_beneficio WHERE solicitacao_id=? AND analista_id=?",Long.class,id,actor)==1)return;throw notFound();}
    private void authorizeWrite(UUID actor,UUID id){Map<String,Object> r=requestRow(id);authorize(actor,(UUID)r.get("paciente_id"));}
    private Map<String,Object> requestRow(UUID id){try{return jdbc.queryForMap("SELECT * FROM solicitacao_beneficio WHERE id=?",id);}catch(EmptyResultDataAccessException ex){throw notFound();}}
    private Map<String,Object> requestForUpdate(UUID id){try{return jdbc.queryForMap("SELECT * FROM solicitacao_beneficio WHERE id=? FOR UPDATE",id);}catch(EmptyResultDataAccessException ex){throw notFound();}}
    private void requireDimension(UUID id,BenefitDimension d){if(jdbc.queryForObject("SELECT count(*) FROM solicitacao_dimensao WHERE solicitacao_id=? AND dimensao=?",Long.class,id,d.name())!=1)throw error(HttpStatus.BAD_REQUEST,"INVALID_INPUT","A evidência não corresponde a uma dimensão solicitada.");}
    private void requireVersion(Map<String,Object> row,long v){if(((Number)row.get("version")).longValue()!=v)throw error(HttpStatus.PRECONDITION_FAILED,"VERSION_MISMATCH","A solicitação mudou; recarregue antes de continuar.");}
    private Map<String,Object> activePolicy(UUID id){try{Map<String,Object> p=jdbc.queryForMap("SELECT * FROM politica_beneficio WHERE id=? AND estado='ATIVA' AND vigencia_inicio<=clock_timestamp() AND (vigencia_fim IS NULL OR vigencia_fim>clock_timestamp())",id);return p;}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","Não há política de benefício vigente para esta solicitação.");}}
    private void institutionMember(UUID user,UUID request){UUID institution=jdbc.queryForObject("SELECT instituicao_id FROM solicitacao_beneficio WHERE id=?",UUID.class,request);if(jdbc.queryForObject("SELECT count(*) FROM membro_instituicao WHERE instituicao_id=? AND usuario_id=? AND papel='ANALISTA_BENEFICIO' AND revogado_em IS NULL",Long.class,institution,user)!=1)throw notFound();}
    private void requireAnalyst(UUID user){if(jdbc.queryForObject("SELECT count(*) FROM membro_instituicao WHERE usuario_id=? AND papel='ANALISTA_BENEFICIO' AND revogado_em IS NULL",Long.class,user)==0)throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","Analista de benefício não encontrado.");}
    private static BenefitServiceException notFound(){return error(HttpStatus.NOT_FOUND,"NOT_FOUND","Solicitação não encontrada.");}
    private static BenefitServiceException error(HttpStatus s,String c,String m){return new BenefitServiceException(s,c,m);}
    static final class BenefitServiceException extends org.springframework.web.server.ResponseStatusException{private final ApiError body;BenefitServiceException(HttpStatus s,String c,String m){super(s,m);body=new ApiError(c,m,UUID.randomUUID(),s.is5xxServerError());}ApiError body(){return body;}}
}
