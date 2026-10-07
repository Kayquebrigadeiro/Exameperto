package br.com.exameperto.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CustodyService {
    private static final int MAX_CODE_ATTEMPTS = 5;
    private final JdbcTemplate jdbc;
    private final DataProtector protector;
    private final ObjectMapper json;
    private final PayoutService payouts;

    CustodyService(JdbcTemplate jdbc, DataProtector protector, ObjectMapper json, PayoutService payouts) {
        this.jdbc = jdbc; this.protector = protector; this.json = json; this.payouts = payouts;
    }

    @Transactional
    CustodyView pickup(UUID actor, UUID orderId, long expectedVersion, String key, PickupConfirmation input) {
        requireKey(key); byte[] requestHash = hash(input);
        CustodyView replay = replay(actor, "PICKUP:" + orderId, key, requestHash);
        if (replay != null) return replay;
        Map<String,Object> order = lockOrder(orderId); replay = replay(actor, "PICKUP:" + orderId, key, requestHash);
        if (replay != null) return replay;
        requireAssignedDriver(actor, orderId); requireVersion(order, expectedVersion);
        if (!"ACEITA".equals(order.get("estado"))) throw error(HttpStatus.CONFLICT,"INVALID_STATE","A retirada só pode ocorrer antes do início da entrega.");
        Map<String,Object> authorization = authorization(orderId);
        if (!"VERIFICADA".equals(authorization.get("estado")) || authorization.get("revogada_em") != null
            || ((Timestamp) authorization.get("valida_ate")).toInstant().isBefore(Instant.now()))
            throw error(HttpStatus.UNPROCESSABLE_ENTITY,"PICKUP_NOT_AUTHORIZED","A autorização de retirada não está vigente.");
        UUID protocol = (UUID) order.get("protocolo_id");
        if (protocol == null || jdbc.queryForObject("SELECT count(*) FROM protocolo_custodia WHERE id=? AND unidade_id=? AND estado='HABILITADO' AND cobertura_retorno_confirmada AND inicio<=clock_timestamp() AND (fim IS NULL OR fim>clock_timestamp())",Long.class,protocol,order.get("unidade_id")) != 1)
            throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","Protocolo e cobertura de retorno não estão habilitados.");
        requireEvidence(actor, input.evidenceDocumentId());
        Map<String,Object> assignment = activeAssignment(orderId);
        UUID custody = UUID.randomUUID();
        jdbc.update("INSERT INTO custodia(id,pedido_id,designacao_id,protocolo_id,retirada_em,retirada_evidencia_id) VALUES (?,?,?,?,clock_timestamp(),?)",custody,orderId,assignment.get("id"),protocol,input.evidenceDocumentId());
        long next = ((Number)order.get("version")).longValue()+1;
        jdbc.update("UPDATE pedido SET estado='RETIRADA',version=?,updated_at=clock_timestamp() WHERE id=? AND version=?",next,orderId,expectedVersion);
        jdbc.update("INSERT INTO evento_custodia(id,pedido_id,custodia_id,designacao_id,ator_id,tipo,estado_anterior,estado_novo,evidencia_id) VALUES (?,?,?,?,?,'RETIRADA','ACEITA','RETIRADA',?)",UUID.randomUUID(),orderId,custody,assignment.get("id"),actor,input.evidenceDocumentId());
        remember(actor,"PICKUP:"+orderId,key,requestHash,orderId,custody);
        return custody(custody);
    }

    @Transactional
    CustodyView startDelivery(UUID actor, UUID orderId, long expectedVersion, String key) {
        requireKey(key); byte[] requestHash = hash(orderId.toString()); CustodyView replay = replay(actor,"START_DELIVERY:"+orderId,key,requestHash); if(replay!=null)return replay;
        Map<String,Object> order=lockOrder(orderId); replay=replay(actor,"START_DELIVERY:"+orderId,key,requestHash); if(replay!=null)return replay;
        requireVersion(order,expectedVersion); requireAssignedDriver(actor,orderId);
        if(!"RETIRADA".equals(order.get("estado")))throw error(HttpStatus.CONFLICT,"INVALID_STATE","O deslocamento só pode iniciar após a retirada.");
        UUID custody=(UUID)jdbc.queryForObject("SELECT id FROM custodia WHERE pedido_id=? AND encerrada_em IS NULL FOR UPDATE",UUID.class,orderId); long next=((Number)order.get("version")).longValue()+1;
        jdbc.update("UPDATE pedido SET estado='EM_ENTREGA',version=?,updated_at=clock_timestamp() WHERE id=? AND version=?",next,orderId,expectedVersion);
        jdbc.update("INSERT INTO evento_custodia(id,pedido_id,custodia_id,designacao_id,ator_id,tipo,estado_anterior,estado_novo) SELECT ?,?,?,d.id,?,'INICIO_ENTREGA','RETIRADA','EM_ENTREGA' FROM designacao d WHERE d.pedido_id=? AND d.encerrada_em IS NULL",UUID.randomUUID(),orderId,custody,actor,orderId);
        remember(actor,"START_DELIVERY:"+orderId,key,requestHash,orderId,custody); return custody(custody);
    }

    @Transactional
    ReceiptChallenge issueCode(UUID actor, UUID orderId, long expectedVersion) {
        Map<String,Object> order=lockOrder(orderId); requireVersion(order,expectedVersion); requireRecipient(actor,order);
        if(!List.of("RETIRADA","EM_ENTREGA").contains(order.get("estado")))throw error(HttpStatus.CONFLICT,"INVALID_STATE","O código só pode ser emitido durante a custódia.");
        long recent=jdbc.queryForObject("SELECT count(*) FROM codigo_recebimento WHERE pedido_id=? AND emitido_em>clock_timestamp()-interval '1 hour'",Long.class,orderId); if(recent>=5)throw error(HttpStatus.TOO_MANY_REQUESTS,"CODE_RATE_LIMIT","Limite de emissão de códigos atingido.");
        jdbc.update("UPDATE codigo_recebimento SET invalidado_em=clock_timestamp() WHERE pedido_id=? AND usado_em IS NULL AND invalidado_em IS NULL",orderId);
        String code=protector.token(); UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO codigo_recebimento(id,pedido_id,destinatario_id,hash,expira_em,max_tentativas) VALUES (?,?,?,?,clock_timestamp()+interval '30 minutes',?)",id,orderId,actor,protector.tokenHash(code),MAX_CODE_ATTEMPTS);
        return new ReceiptChallenge(code,Instant.now().plusSeconds(1800));
    }

    @Transactional
    CustodyView deliver(UUID actor, UUID orderId, long expectedVersion, String key, DeliveryCode input) {
        requireKey(key); byte[] requestHash=hash(input); CustodyView replay=replay(actor,"DELIVER:"+orderId,key,requestHash); if(replay!=null)return replay;
        Map<String,Object> order=lockOrder(orderId); replay=replay(actor,"DELIVER:"+orderId,key,requestHash); if(replay!=null)return replay;
        requireVersion(order,expectedVersion); requireAssignedDriver(actor,orderId); if(!"EM_ENTREGA".equals(order.get("estado")))throw error(HttpStatus.CONFLICT,"INVALID_STATE","A entrega só pode ser confirmada durante o deslocamento.");
        requireRecipientCurrent(order); Map<String,Object> code;
        try { code=jdbc.queryForMap("SELECT * FROM codigo_recebimento WHERE pedido_id=? AND usado_em IS NULL AND invalidado_em IS NULL FOR UPDATE",orderId); } catch(EmptyResultDataAccessException ex){throw error(HttpStatus.UNPROCESSABLE_ENTITY,"RECEIPT_CODE_REQUIRED","O destinatário ainda não emitiu um código válido.");}
        if(((Timestamp)code.get("expira_em")).toInstant().isBefore(Instant.now()) || ((Number)code.get("tentativas")).intValue()>=((Number)code.get("max_tentativas")).intValue()) throw error(HttpStatus.UNPROCESSABLE_ENTITY,"RECEIPT_CODE_INVALID","Código expirado ou bloqueado.");
        if(!MessageDigest.isEqual((byte[])code.get("hash"),protector.tokenHash(input.code()))){jdbc.update("UPDATE codigo_recebimento SET tentativas=tentativas+1,invalidado_em=CASE WHEN tentativas+1>=max_tentativas THEN clock_timestamp() ELSE invalidado_em END WHERE id=?",code.get("id"));throw error(HttpStatus.UNPROCESSABLE_ENTITY,"RECEIPT_CODE_INVALID","Código inválido.");}
        UUID custody=(UUID)jdbc.queryForObject("SELECT id FROM custodia WHERE pedido_id=? AND encerrada_em IS NULL FOR UPDATE",UUID.class,orderId); UUID event=UUID.randomUUID();
        jdbc.update("UPDATE codigo_recebimento SET usado_em=clock_timestamp() WHERE id=?",code.get("id")); jdbc.update("UPDATE custodia SET destino_tipo='ENTREGA',destino_usuario_id=?,encerrada_em=clock_timestamp(),recebimento_evento_id=?,version=version+1 WHERE id=?",order.get("destinatario_id"),event,custody);
        long next=((Number)order.get("version")).longValue()+1; jdbc.update("UPDATE pedido SET estado='ENTREGUE',version=?,updated_at=clock_timestamp() WHERE id=? AND version=?",next,orderId,expectedVersion); jdbc.update("UPDATE designacao SET encerrada_em=clock_timestamp(),encerramento_motivo='ENTREGA_COMPROVADA',version=version+1 WHERE pedido_id=? AND encerrada_em IS NULL",orderId);
        jdbc.update("INSERT INTO evento_custodia(id,pedido_id,custodia_id,designacao_id,ator_id,tipo,estado_anterior,estado_novo) SELECT ?,?,?,d.id,?,'ENTREGA_COMPROVADA','EM_ENTREGA','ENTREGUE' FROM designacao d WHERE d.pedido_id=?",event,orderId,custody,actor,orderId);
        payouts.recordCompletedService(orderId);
        remember(actor,"DELIVER:"+orderId,key,requestHash,orderId,custody); return custody(custody);
    }

    @Transactional
    IncidentView incident(UUID actor, UUID orderId, long expectedVersion, String key, IncidentInput input) {
        requireKey(key); byte[] requestHash=hash(input); IncidentView replayIncident=replayIncident(actor,"INCIDENT:"+orderId,key,requestHash); if(replayIncident!=null)return replayIncident;
        Map<String,Object> order=lockOrder(orderId); replayIncident=replayIncident(actor,"INCIDENT:"+orderId,key,requestHash); if(replayIncident!=null)return replayIncident;
        requireVersion(order,expectedVersion); boolean driver=assignedDriver(actor,orderId); if(!driver&&!isParticipant(actor,orderId))throw notFound();
        if(!List.of("ACEITA","RETIRADA","EM_ENTREGA","OCORRENCIA").contains(order.get("estado")))throw error(HttpStatus.CONFLICT,"INVALID_STATE","Não é possível abrir ocorrência neste estado.");
        if(!List.of("UNIDADE_FECHADA","DESTINATARIO_AUSENTE","LACRE_COMPROMETIDO","OUTRA").contains(input.type()))throw error(HttpStatus.BAD_REQUEST,"INVALID_INPUT","Tipo de ocorrência inválido.");
        UUID custody=nullableCustody(orderId), assignment=(UUID)activeAssignment(orderId).get("id"), id=UUID.randomUUID();
        jdbc.update("INSERT INTO ocorrencia_entrega(id,pedido_id,custodia_id,designacao_id,aberta_por,tipo,descricao,estado) VALUES (?,?,?,?,?,?,?,'ABERTA')",id,orderId,custody,assignment,actor,input.type(),input.description());
        String prior=String.valueOf(order.get("estado")); if(!"OCORRENCIA".equals(prior)){jdbc.update("UPDATE pedido SET estado='OCORRENCIA',version=version+1,updated_at=clock_timestamp() WHERE id=? AND version=?",orderId,expectedVersion);}
        jdbc.update("INSERT INTO evento_custodia(id,pedido_id,custodia_id,designacao_id,ator_id,tipo,estado_anterior,estado_novo,motivo_codigo) VALUES (?,?,?,?,?,'OCORRENCIA',?,?,?)",UUID.randomUUID(),orderId,custody,assignment,actor,prior,"OCORRENCIA",input.type());
        remember(actor,"INCIDENT:"+orderId,key,requestHash,orderId,id); return incident(id);
    }

    CustodyView custodyFor(UUID actor,UUID orderId){lockless(orderId);if(!isParticipant(actor,orderId)&&!assignedDriver(actor,orderId))throw notFound();return jdbc.queryForObject("SELECT id FROM custodia WHERE pedido_id=?",(rs,n)->custody(rs.getObject(1,UUID.class)),orderId);}
    List<IncidentView> incidents(UUID actor,UUID orderId){if(!isParticipant(actor,orderId)&&!assignedDriver(actor,orderId))throw notFound();return jdbc.query("SELECT id FROM ocorrencia_entrega WHERE pedido_id=? ORDER BY created_at,id",(rs,n)->incident(rs.getObject(1,UUID.class)),orderId);}

    private void requireEvidence(UUID actor,UUID doc){try{Map<String,Object>d=jdbc.queryForMap("SELECT proprietario_id,categoria,estado FROM documento WHERE id=?",doc);if(!actor.equals(d.get("proprietario_id"))||!"COMPROVANTE".equals(d.get("categoria"))||!"INSPECAO_APROVADA".equals(d.get("estado")))throw notFound();}catch(EmptyResultDataAccessException ex){throw notFound();}}
    private void requireRecipientCurrent(Map<String,Object> order){UUID recipient=(UUID)order.get("destinatario_id");if(jdbc.queryForObject("SELECT count(*) FROM usuario WHERE id=? AND estado='ATIVO'",Long.class,recipient)!=1||!recipientAuthorized(order,recipient))throw error(HttpStatus.FORBIDDEN,"RECIPIENT_UNAUTHORIZED","O destinatário não está autorizado.");}
    private boolean recipientAuthorized(Map<String,Object> order,UUID actor){return jdbc.queryForObject("SELECT count(*) FROM paciente WHERE id=? AND usuario_id=?",Long.class,order.get("paciente_id"),actor)==1||jdbc.queryForObject("SELECT count(*) FROM autorizacao_paciente a JOIN autorizacao_escopo e ON e.autorizacao_id=a.id WHERE a.paciente_id=? AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() AND e.escopo='RECEBIMENTO'",Long.class,order.get("paciente_id"),actor)==1;}
    private void requireRecipient(UUID actor,Map<String,Object> order){UUID recipient=(UUID)order.get("destinatario_id");if(recipient.equals(actor)){requireRecipientCurrent(order);return;}if(!recipientAuthorized(order,actor))throw notFound();}
    private void requireAssignedDriver(UUID actor,UUID order){if(!assignedDriver(actor,order))throw notFound();}
    private boolean assignedDriver(UUID actor,UUID order){return jdbc.queryForObject("SELECT count(*) FROM designacao d JOIN entregador e ON e.id=d.entregador_id JOIN usuario u ON u.id=e.usuario_id WHERE d.pedido_id=? AND e.usuario_id=? AND d.encerrada_em IS NULL AND e.estado='APROVADO' AND u.estado='ATIVO'",Long.class,order,actor)==1;}
    private boolean isParticipant(UUID actor,UUID order){return jdbc.queryForObject("SELECT count(*) FROM pedido p JOIN paciente pt ON pt.id=p.paciente_id LEFT JOIN autorizacao_paciente a ON a.paciente_id=p.paciente_id AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() LEFT JOIN autorizacao_escopo e ON e.autorizacao_id=a.id AND e.escopo='RECEBIMENTO' WHERE p.id=? AND (pt.usuario_id=? OR (p.destinatario_id=? AND (pt.usuario_id=? OR e.id IS NOT NULL)) OR e.id IS NOT NULL)",Long.class,actor,order,actor,actor,actor)>0;}
    private Map<String,Object> activeAssignment(UUID id){return jdbc.queryForMap("SELECT * FROM designacao WHERE pedido_id=? AND encerrada_em IS NULL",id);}
    private UUID nullableCustody(UUID id){try{return jdbc.queryForObject("SELECT id FROM custodia WHERE pedido_id=?",UUID.class,id);}catch(EmptyResultDataAccessException ex){return null;}}
    private Map<String,Object> authorization(UUID id){return jdbc.queryForMap("SELECT * FROM autorizacao_retirada WHERE pedido_id=?",id);}
    private Map<String,Object> lockOrder(UUID id){try{return jdbc.queryForMap("SELECT * FROM pedido WHERE id=? FOR UPDATE",id);}catch(EmptyResultDataAccessException ex){throw notFound();}}
    private Map<String,Object> lockless(UUID id){try{return jdbc.queryForMap("SELECT * FROM pedido WHERE id=?",id);}catch(EmptyResultDataAccessException ex){throw notFound();}}
    private void requireVersion(Map<String,Object> row,long v){if(((Number)row.get("version")).longValue()!=v)throw error(HttpStatus.PRECONDITION_FAILED,"VERSION_MISMATCH","O pedido mudou; recarregue antes de continuar.");}
    private CustodyView custody(UUID id){return jdbc.queryForObject("SELECT id,pedido_id,destino_tipo,retirada_em,encerrada_em,version FROM custodia WHERE id=?",(rs,n)->new CustodyView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getTimestamp(5)==null?"ATIVA":"ENCERRADA",rs.getString(3),rs.getTimestamp(4).toInstant(),rs.getTimestamp(5)==null?null:rs.getTimestamp(5).toInstant(),rs.getLong(6)),id);}
    private IncidentView incident(UUID id){return jdbc.queryForObject("SELECT id,pedido_id,tipo,estado,descricao,created_at FROM ocorrencia_entrega WHERE id=?",(rs,n)->new IncidentView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getString(4),rs.getString(5),rs.getTimestamp(6).toInstant()),id);}
    private void requireKey(String key){if(key==null||key.isBlank())throw error(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_REQUIRED","A operação exige Idempotency-Key.");}
    private void remember(UUID actor,String op,String key,byte[] hash,UUID order,UUID result){jdbc.update("INSERT INTO custodia_idempotencia(ator_id,operacao,chave,request_hash,pedido_id,resultado_id) VALUES (?,?,?,?,?,?)",actor,op,key,hash,order,result);}
    private CustodyView replay(UUID actor,String op,String key,byte[] hash){try{Map<String,Object> r=jdbc.queryForMap("SELECT request_hash,resultado_id FROM custodia_idempotencia WHERE ator_id=? AND operacao=? AND chave=?",actor,op,key);if(!MessageDigest.isEqual((byte[])r.get("request_hash"),hash))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","A chave já foi usada com dados diferentes.");return custody((UUID)r.get("resultado_id"));}catch(EmptyResultDataAccessException ex){return null;}}
    private IncidentView replayIncident(UUID actor,String op,String key,byte[] hash){try{Map<String,Object> r=jdbc.queryForMap("SELECT request_hash,resultado_id FROM custodia_idempotencia WHERE ator_id=? AND operacao=? AND chave=?",actor,op,key);if(!MessageDigest.isEqual((byte[])r.get("request_hash"),hash))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","A chave já foi usada com dados diferentes.");return incident((UUID)r.get("resultado_id"));}catch(EmptyResultDataAccessException ex){return null;}}
    private byte[] hash(Object value){try{return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static OrderService.OrderException notFound(){return error(HttpStatus.NOT_FOUND,"NOT_FOUND","Pedido não encontrado.");}
    private static OrderService.OrderException error(HttpStatus s,String c,String m){return OrderService.error(s,c,m);}
}
