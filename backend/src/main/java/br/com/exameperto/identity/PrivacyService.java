package br.com.exameperto.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PrivacyService {
    private static final String POLICY_CATEGORY = "CONTA";
    private static final String POLICY_PURPOSE = "ATENDER_EXCLUSAO_TITULAR";
    private static final String POLICY_TRIGGER = "SOLICITACAO_RESPONDIDA";

    private final JdbcTemplate jdbc;
    private final DataProtector protector;
    private final MfaService mfa;
    private final PrivateObjectStore objects;
    private final TrackingLiveRegistry live;
    private final boolean purgeEnabled;

    PrivacyService(JdbcTemplate jdbc, DataProtector protector, MfaService mfa,
                   PrivateObjectStore objects, TrackingLiveRegistry live,
                   @Value("${privacy.purge-enabled:false}") boolean purgeEnabled) {
        this.jdbc=jdbc; this.protector=protector; this.mfa=mfa;
        this.objects=objects; this.live=live; this.purgeEnabled=purgeEnabled;
    }

    @Transactional
    PrivacyRequestView create(UUID actor, PrivacyRequestInput input, String idempotencyKey) {
        String key=normalizeKey(idempotencyKey);
        byte[] requestHash=sha256(input.type()+"\n"+(input.description()==null?"":input.description()));
        if (key!=null) {
            Map<String,Object> existing=findByKey(actor,key);
            if (existing!=null) return replay(actor,existing,requestHash);
        }
        UUID id=UUID.randomUUID();
        String protocol="PRV-"+id.toString().replace("-","").substring(0,20).toUpperCase();
        try {
            jdbc.update("INSERT INTO solicitacao_privacidade(id,protocolo,usuario_id,idempotency_key,request_hash,tipo,descricao_cifrada,estado) VALUES (?,?,?,?,?,?,?,'PENDENTE')",
                id,protocol,actor,key,requestHash,input.type().name(),input.description()==null?null:protector.encrypt(input.description()));
        } catch (DuplicateKeyException ex) {
            return replay(actor,findByKey(actor,key),requestHash);
        }
        return view(actor,id);
    }

    PrivacyRequestView get(UUID actor, UUID id) { owner(actor,id); return viewAny(id); }

    @Transactional
    PrivacyRequestView respond(AuthService.SessionPrincipal principal, UUID id, PrivacyResponseInput input) {
        privileged(principal);
        int changed=jdbc.update("UPDATE solicitacao_privacidade SET estado=?,resposta_cifrada=?,responsavel_id=?,respondida_em=clock_timestamp(),version=version+1,updated_at=clock_timestamp() WHERE id=? AND estado IN ('PENDENTE','EM_ANALISE')",
            input.status(),protector.encrypt(input.response()),principal.userId(),id);
        if (changed!=1) {
            row(id);
            throw error(HttpStatus.CONFLICT,"INVALID_STATE","A solicitação já foi respondida ou avançou de etapa.");
        }
        return viewAny(id);
    }

    @Transactional
    PrivacyRequestView authorizePurge(AuthService.SessionPrincipal principal, UUID requestId) {
        privileged(principal);
        requirePurgeEnabled();
        Map<String,Object> request=lockedRequest(requestId);
        if (!"EXCLUSAO".equals(request.get("tipo")) || !"RESPONDIDA".equals(request.get("estado")))
            throw error(HttpStatus.CONFLICT,"INVALID_STATE","A exclusão precisa estar respondida antes da autorização do expurgo.");
        UUID owner=(UUID)request.get("usuario_id");
        byte[] ownerHash=protector.lookup(owner.toString());
        if (jdbc.queryForObject("SELECT count(*) FROM retencao_excecao WHERE categoria=? AND referencia_hash=? AND ativa AND revisao_em>=current_date",Long.class,POLICY_CATEGORY,ownerHash)>0)
            throw error(HttpStatus.CONFLICT,"RETENTION_EXCEPTION_ACTIVE","Há exceção de conservação vigente para este escopo.");
        Map<String,Object> policy=activePolicy();
        UUID execution=UUID.randomUUID();
        jdbc.update("INSERT INTO execucao_expurgo(id,politica_id,solicitacao_id,alvo_referencia_cifrada,alvo_referencia_hash,estado,autorizada_por,backup_residual_ate) VALUES (?,?,?,?,?,'AUTORIZADA',?,CASE WHEN ?::integer IS NULL THEN NULL ELSE clock_timestamp()+(?::integer*interval '1 day') END)",
            execution,policy.get("id"),requestId,protector.encrypt(owner.toString()),ownerHash,principal.userId(),policy.get("backup_prazo_dias"),policy.get("backup_prazo_dias"));
        inventory(execution,owner);
        jdbc.update("UPDATE solicitacao_privacidade SET estado='EXPURGO_SOLICITADO',expurgo_id=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",execution,requestId);
        return viewAny(requestId);
    }

    @Transactional
    PrivacyRequestView execute(AuthService.SessionPrincipal principal, UUID requestId) {
        privileged(principal);
        requirePurgeEnabled();
        Map<String,Object> execution=lockedExecution(requestId);
        String state=(String)execution.get("estado");
        if ("EXECUTADA".equals(state)||"VERIFICADA".equals(state)) return viewAny(requestId);
        if (!"AUTORIZADA".equals(state)&&!"FALHA".equals(state))
            throw error(HttpStatus.CONFLICT,"INVALID_STATE","Expurgo não autorizado para execução.");
        UUID executionId=(UUID)execution.get("id");
        UUID owner=(UUID)execution.get("usuario_id");
        boolean failed=false;
        for (Map<String,Object> target : actionableTargets(executionId)) {
            UUID targetId=(UUID)target.get("id");
            try {
                remove((String)target.get("tipo"),protector.decrypt((byte[])target.get("referencia_cifrada")),owner);
                jdbc.update("UPDATE expurgo_alvo SET estado='REMOVIDO',motivo_codigo=NULL,tentativas=tentativas+1 WHERE id=?",targetId);
            } catch (RuntimeException ex) {
                failed=true;
                jdbc.update("UPDATE expurgo_alvo SET estado='FALHA',motivo_codigo='RETRY_REQUIRED',tentativas=tentativas+1 WHERE id=?",targetId);
            }
        }
        if (failed) {
            jdbc.update("UPDATE execucao_expurgo SET estado='FALHA',erro_codigo='TARGET_FAILURE',executada_por=? WHERE id=?",principal.userId(),executionId);
            return viewAny(requestId);
        }
        jdbc.update("INSERT INTO tombstone_expurgo(id,execucao_id,referencia_hash,categoria) VALUES (?,?,?,'CONTA') ON CONFLICT DO NOTHING",UUID.randomUUID(),executionId,execution.get("alvo_referencia_hash"));
        jdbc.update("UPDATE execucao_expurgo SET estado='EXECUTADA',erro_codigo=NULL,executada_por=?,executada_em=clock_timestamp() WHERE id=?",principal.userId(),executionId);
        jdbc.update("UPDATE solicitacao_privacidade SET estado='EXPURGADA',version=version+1,updated_at=clock_timestamp() WHERE id=?",requestId);
        return viewAny(requestId);
    }

    @Transactional
    PrivacyRequestView verify(AuthService.SessionPrincipal principal, UUID requestId) {
        privileged(principal);
        requirePurgeEnabled();
        Map<String,Object> execution=lockedExecution(requestId);
        if ("VERIFICADA".equals(execution.get("estado"))) return viewAny(requestId);
        if (!"EXECUTADA".equals(execution.get("estado")))
            throw error(HttpStatus.CONFLICT,"INVALID_STATE","A execução precisa terminar antes da verificação.");
        UUID executionId=(UUID)execution.get("id");
        UUID owner=(UUID)execution.get("usuario_id");
        for (Map<String,Object> target : jdbc.queryForList("SELECT id,tipo,referencia_cifrada FROM expurgo_alvo WHERE execucao_id=? AND estado='REMOVIDO' ORDER BY id",executionId)) {
            String reference=protector.decrypt((byte[])target.get("referencia_cifrada"));
            if (!absent((String)target.get("tipo"),reference,owner))
                throw error(HttpStatus.CONFLICT,"PURGE_NOT_VERIFIED","Ainda há conteúdo no recurso controlado.");
            jdbc.update("UPDATE expurgo_alvo SET estado='VERIFICADO' WHERE id=?",target.get("id"));
        }
        jdbc.update("UPDATE execucao_expurgo SET estado='VERIFICADA',verificada_por=?,verificada_em=clock_timestamp() WHERE id=?",principal.userId(),executionId);
        jdbc.update("UPDATE solicitacao_privacidade SET estado='VERIFICADA',version=version+1,updated_at=clock_timestamp() WHERE id=?",requestId);
        return viewAny(requestId);
    }

    private void inventory(UUID execution, UUID owner) {
        for (Map<String,Object> document : jdbc.queryForList("SELECT id,objeto_chave FROM documento WHERE proprietario_id=? AND categoria NOT IN ('FINANCIAMENTO','COMPROVANTE') AND estado<>'EXPURGADO'",owner)) {
            target(execution,"OBJETO",(String)document.get("objeto_chave"),"PENDENTE",null);
            target(execution,"BANCO","DOCUMENTO:"+document.get("id"),"PENDENTE",null);
        }
        for (UUID position : jdbc.queryForList("SELECT DISTINCT p.id FROM posicao_tarefa p JOIN pedido pe ON pe.id=p.pedido_id LEFT JOIN paciente pa ON pa.id=pe.paciente_id LEFT JOIN designacao d ON d.id=p.designacao_id LEFT JOIN entregador e ON e.id=d.entregador_id WHERE pe.solicitante_id=? OR pe.destinatario_id=? OR pa.usuario_id=? OR e.usuario_id=?",UUID.class,owner,owner,owner,owner))
            target(execution,"BANCO","POSICAO:"+position,"PENDENTE",null);
        for (UUID queued : jdbc.queryForList("SELECT id FROM outbox WHERE payload_saneado->>'usuarioId'=?",UUID.class,owner.toString()))
            target(execution,"FILA","OUTBOX:"+queued,"PENDENTE",null);
        target(execution,"CACHE","USUARIO:"+owner,"PENDENTE",null);
        target(execution,"VERSAO","ARMAZENAMENTO_LOCAL_SEM_VERSIONAMENTO","NAO_APLICAVEL","NOT_VERSIONED");
        target(execution,"TEMPORARIO","TEMPORARIOS_NAO_VINCULADOS_A_CONTA","PROCEDIMENTO_PENDENTE","PROCEDURE_REQUIRED");
        target(execution,"REFERENCIA","FINANCEIRO:"+owner,"PRESERVADO","FINANCIAL_RETENTION_PENDING");
        target(execution,"DISPOSITIVO","DISPOSITIVOS_DO_TITULAR","PROCEDIMENTO_PENDENTE","OUTSIDE_SERVER_CONTROL");
        target(execution,"FORNECEDOR","SUBOPERADORES_EXTERNOS","PROCEDIMENTO_PENDENTE","CONTRACT_REQUIRED");
        target(execution,"BACKUP","COPIAS_RECUPERAVEIS","PROCEDIMENTO_PENDENTE","RESTORE_PROCEDURE_REQUIRED");
    }

    private void target(UUID execution,String type,String reference,String state,String reason) {
        jdbc.update("INSERT INTO expurgo_alvo(id,execucao_id,tipo,referencia_cifrada,estado,motivo_codigo) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),execution,type,protector.encrypt(reference),state,reason);
    }

    private List<Map<String,Object>> actionableTargets(UUID execution) {
        return jdbc.queryForList("SELECT id,tipo,referencia_cifrada FROM expurgo_alvo WHERE execucao_id=? AND estado IN ('PENDENTE','FALHA') ORDER BY CASE tipo WHEN 'OBJETO' THEN 1 WHEN 'BANCO' THEN 2 WHEN 'FILA' THEN 3 WHEN 'CACHE' THEN 4 ELSE 5 END,id",execution);
    }

    private void remove(String type,String reference,UUID owner) {
        switch (type) {
            case "OBJETO" -> objects.remove(reference);
            case "BANCO" -> {
                if (reference.startsWith("DOCUMENTO:")) jdbc.update("UPDATE documento SET objeto_chave='tombstone/'||id,sha256=decode(md5(id::text),'hex'),mime='application/x-deleted',tamanho=1,estado='EXPURGADO',updated_at=clock_timestamp(),version=version+1 WHERE id=? AND proprietario_id=? AND categoria NOT IN ('FINANCIAMENTO','COMPROVANTE')",UUID.fromString(reference.substring(10)),owner);
                else if (reference.startsWith("POSICAO:")) jdbc.update("DELETE FROM posicao_tarefa WHERE id=?",UUID.fromString(reference.substring(8)));
                else throw new IllegalStateException("Referência de banco desconhecida.");
            }
            case "FILA" -> jdbc.update("DELETE FROM outbox WHERE id=? AND payload_saneado->>'usuarioId'=?",UUID.fromString(reference.substring(7)),owner.toString());
            case "CACHE" -> live.revokeUser(owner);
            default -> throw new IllegalStateException("Tipo de recurso não executável.");
        }
    }

    private boolean absent(String type,String reference,UUID owner) {
        return switch (type) {
            case "OBJETO" -> !objects.exists(reference);
            case "BANCO" -> reference.startsWith("DOCUMENTO:")
                ? jdbc.queryForObject("SELECT count(*) FROM documento WHERE id=? AND proprietario_id=? AND (estado<>'EXPURGADO' OR mime<>'application/x-deleted')",Long.class,UUID.fromString(reference.substring(10)),owner)==0
                : jdbc.queryForObject("SELECT count(*) FROM posicao_tarefa WHERE id=?",Long.class,UUID.fromString(reference.substring(8)))==0;
            case "FILA" -> jdbc.queryForObject("SELECT count(*) FROM outbox WHERE id=?",Long.class,UUID.fromString(reference.substring(7)))==0;
            case "CACHE" -> !live.hasUser(owner);
            default -> false;
        };
    }

    private void privileged(AuthService.SessionPrincipal principal) { mfa.requireVerified(principal); operator(principal.userId()); }
    private void operator(UUID user) {
        if (jdbc.queryForObject("SELECT count(*) FROM papel_global WHERE usuario_id=? AND papel='ANALISTA_OPERACIONAL' AND revogado_em IS NULL",Long.class,user)!=1)
            throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","Operador de privacidade não encontrado.");
    }
    private void requirePurgeEnabled() {
        if (!purgeEnabled) throw error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","Expurgo operacional está desabilitado.");
    }
    private Map<String,Object> activePolicy() {
        try { return jdbc.queryForMap("SELECT p.* FROM politica_retencao p JOIN usuario r ON r.id=p.responsavel_id AND r.estado='ATIVO' WHERE p.categoria=? AND p.finalidade_codigo=? AND p.gatilho=? AND p.validada_em IS NOT NULL AND p.validada_em<=clock_timestamp() AND p.desativada_em IS NULL AND p.prazo_dias IS NOT NULL AND p.responsavel_id IS NOT NULL AND p.base_validada AND p.verificacao_descarte ORDER BY p.numero DESC LIMIT 1",POLICY_CATEGORY,POLICY_PURPOSE,POLICY_TRIGGER); }
        catch (EmptyResultDataAccessException ex) { throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","Não há política de retenção validada para esta categoria, finalidade e gatilho."); }
    }
    private Map<String,Object> lockedRequest(UUID id) { row(id); return jdbc.queryForMap("SELECT * FROM solicitacao_privacidade WHERE id=? FOR UPDATE",id); }
    private Map<String,Object> lockedExecution(UUID requestId) {
        try { return jdbc.queryForMap("SELECT e.*,s.usuario_id FROM execucao_expurgo e JOIN solicitacao_privacidade s ON s.id=e.solicitacao_id WHERE s.id=? FOR UPDATE",requestId); }
        catch (EmptyResultDataAccessException ex) { throw error(HttpStatus.CONFLICT,"INVALID_STATE","Não existe expurgo autorizado para esta solicitação."); }
    }
    private Map<String,Object> findByKey(UUID actor,String key) {
        try { return jdbc.queryForMap("SELECT id,request_hash FROM solicitacao_privacidade WHERE usuario_id=? AND idempotency_key=?",actor,key); }
        catch (EmptyResultDataAccessException ex) { return null; }
    }
    private PrivacyRequestView replay(UUID actor,Map<String,Object> existing,byte[] requestHash) {
        if (existing==null || !MessageDigest.isEqual((byte[])existing.get("request_hash"),requestHash))
            throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","A chave já foi usada com outro conteúdo.");
        return view(actor,(UUID)existing.get("id"));
    }
    private String normalizeKey(String key) {
        if (key==null||key.isBlank()) return null;
        if (key.length()>128) throw error(HttpStatus.BAD_REQUEST,"INVALID_INPUT","Idempotency-Key excede 128 caracteres.");
        return key;
    }
    private void owner(UUID actor,UUID id) {
        if (jdbc.queryForObject("SELECT count(*) FROM solicitacao_privacidade WHERE id=? AND usuario_id=?",Long.class,id,actor)!=1)
            throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Solicitação não encontrada.");
    }
    private Map<String,Object> row(UUID id) {
        try { return jdbc.queryForMap("SELECT * FROM solicitacao_privacidade WHERE id=?",id); }
        catch (EmptyResultDataAccessException ex) { throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Solicitação não encontrada."); }
    }
    private PrivacyRequestView view(UUID actor,UUID id) { owner(actor,id); return viewAny(id); }
    private PrivacyRequestView viewAny(UUID id) {
        Map<String,Object> row=row(id);
        Map<String,Object> execution=row.get("expurgo_id")==null?null:jdbc.queryForMap("SELECT estado,executada_em,verificada_em FROM execucao_expurgo WHERE id=?",row.get("expurgo_id"));
        int pending=0,preserved=0;
        if (row.get("expurgo_id")!=null) {
            pending=jdbc.queryForObject("SELECT count(*) FROM expurgo_alvo WHERE execucao_id=? AND estado='PROCEDIMENTO_PENDENTE'",Integer.class,row.get("expurgo_id"));
            preserved=jdbc.queryForObject("SELECT count(*) FROM expurgo_alvo WHERE execucao_id=? AND estado='PRESERVADO'",Integer.class,row.get("expurgo_id"));
        }
        byte[] response=(byte[])row.get("resposta_cifrada");
        return new PrivacyRequestView(id,(String)row.get("protocolo"),(String)row.get("tipo"),(String)row.get("estado"),instant(row.get("created_at")),response==null?null:protector.decrypt(response),execution==null?null:(String)execution.get("estado"),execution==null?null:instant(execution.get("executada_em")),execution==null?null:instant(execution.get("verificada_em")),pending,preserved,((Number)row.get("version")).longValue());
    }
    private static Instant instant(Object value) { return value==null?null:((Timestamp)value).toInstant(); }
    private static byte[] sha256(String value) { try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    static PrivacyException error(HttpStatus status,String code,String message) { return new PrivacyException(status,code,message); }
    static final class PrivacyException extends org.springframework.web.server.ResponseStatusException {
        private final ApiError body;
        PrivacyException(HttpStatus status,String code,String message) { super(status,message); body=new ApiError(code,message,UUID.randomUUID(),status.is5xxServerError()); }
        ApiError body() { return body; }
    }
}
