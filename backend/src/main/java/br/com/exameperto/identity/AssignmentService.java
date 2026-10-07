package br.com.exameperto.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AssignmentService {
    private final JdbcTemplate jdbc; private final DataProtector protector; private final ObjectMapper json;
    AssignmentService(JdbcTemplate jdbc,DataProtector protector,ObjectMapper json){this.jdbc=jdbc;this.protector=protector;this.json=json;}

    List<OfferView> offers(UUID actor){
        approvedDriver(actor); requireActivePolicy();
        if(jdbc.queryForObject("SELECT count(*) FROM vinculo_veiculo vv JOIN veiculo v ON v.id=vv.veiculo_id JOIN entregador e ON e.id=vv.entregador_id WHERE e.usuario_id=? AND vv.estado='APROVADO' AND v.estado='APROVADO' AND (vv.valido_ate IS NULL OR vv.valido_ate>=current_date)",Long.class,actor)==0)throw error(HttpStatus.FORBIDDEN,"VEHICLE_NOT_APPROVED","Nenhum vínculo de veículo aprovado e vigente.");
        return jdbc.query("SELECT p.id,p.origem_cifrada,p.destino_cifrada,q.distancia_m,q.frete,q.moeda,p.version,q.politica_cancelamento_id FROM pedido p JOIN orcamento q ON q.pedido_id=p.id AND q.estado='ACEITO' JOIN aceite_orcamento ao ON ao.orcamento_id=q.id JOIN protocolo_custodia pc ON pc.id=p.protocolo_id AND pc.unidade_id=p.unidade_id AND pc.estado='HABILITADO' AND pc.cobertura_retorno_confirmada AND pc.inicio<=clock_timestamp() AND (pc.fim IS NULL OR pc.fim>clock_timestamp()) WHERE p.estado='DISPONIVEL' AND NOT EXISTS (SELECT 1 FROM designacao d WHERE d.pedido_id=p.id AND d.encerrada_em IS NULL) AND ((q.paciente_valor=0) OR EXISTS (SELECT 1 FROM operacao_financeira o WHERE o.pedido_id=p.id AND o.tipo='COBRANCA' AND o.estado='CONFIRMADA')) AND ((q.subsidio_valor=0) OR EXISTS (SELECT 1 FROM reserva_subsidio r WHERE r.orcamento_id=q.id AND r.estado='RESERVADA' AND r.valor-r.valor_liberado-r.valor_liquidado=q.subsidio_valor)) ORDER BY p.created_at,p.id",(rs,n)->new OfferView(rs.getObject(1,UUID.class),city(rs.getBytes(2)),city(rs.getBytes(3)),rs.getInt(4),rs.getBigDecimal(5),rs.getString(6),rs.getLong(7),rs.getObject(8,UUID.class)));
    }

    @Transactional
    AssignmentView accept(UUID actor,UUID orderId,long expectedVersion,String idem,AcceptTaskInput input){
        if(idem==null||idem.isBlank())throw error(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_REQUIRED","O aceite exige Idempotency-Key.");
        byte[] hash=hash(input);AssignmentView replay=replay(actor,orderId,idem,hash);if(replay!=null)return replay;
        Map<String,Object> order=orderForUpdate(orderId);Map<String,Object> driver=approvedDriverForUpdate(actor);UUID driverId=(UUID)driver.get("id");Map<String,Object> link=approvedLinkForUpdate(actor,input.vehicleLinkId());
        replay=replay(actor,orderId,idem,hash);if(replay!=null)return replay;
        if(((Number)order.get("version")).longValue()!=expectedVersion)throw error(HttpStatus.PRECONDITION_FAILED,"VERSION_MISMATCH","A oferta mudou; atualize antes de aceitar.");
        if(!"DISPONIVEL".equals(order.get("estado")))throw error(HttpStatus.CONFLICT,"OFFER_UNAVAILABLE","A oferta não está mais disponível.");
        Map<String,Object> quote=currentQuote(orderId);requireCoverage(orderId,quote);requireProtocol(order);Map<String,Object> policy=requireActivePolicy();
        if(!java.util.Objects.equals(quote.get("politica_cancelamento_id"),input.acceptedCancellationPolicyId()))throw error(HttpStatus.CONFLICT,"ACCEPTED_TERMS_DIVERGE","A política aceita diverge da oferta.");
        int limit=((Number)policy.get("limite_tarefas_simultaneas")).intValue();long active=jdbc.queryForObject("SELECT count(*) FROM designacao WHERE entregador_id=? AND encerrada_em IS NULL",Long.class,driverId);if(active>=limit)throw error(HttpStatus.CONFLICT,"DRIVER_CAPACITY_REACHED","O limite de tarefas simultâneas foi atingido.");
        UUID id=UUID.randomUUID();String snapshot=identitySnapshot(driver,link);
        try{jdbc.update("INSERT INTO designacao(id,pedido_id,entregador_id,vinculo_id,orcamento_id,politica_designacao_id,politica_cancelamento_id,pedido_version,limite_tarefas_snapshot,identificacao_snapshot) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb)",id,orderId,driverId,input.vehicleLinkId(),quote.get("id"),policy.get("id"),input.acceptedCancellationPolicyId(),expectedVersion,limit,snapshot);}
        catch(DataIntegrityViolationException ex){throw error(HttpStatus.CONFLICT,"ALREADY_ASSIGNED","A oferta não está mais disponível.");}
        int changed=jdbc.update("UPDATE pedido SET estado='ACEITA',version=version+1,updated_at=clock_timestamp() WHERE id=? AND version=? AND estado='DISPONIVEL'",orderId,expectedVersion);if(changed!=1)throw error(HttpStatus.CONFLICT,"OFFER_UNAVAILABLE","A oferta não está mais disponível.");
        jdbc.update("INSERT INTO evento_designacao(id,pedido_id,designacao_id,ator_id,tipo,estado_anterior,estado_novo,pedido_version) VALUES (?,?,?,?,'ACEITE','DISPONIVEL','ACEITA',?)",UUID.randomUUID(),orderId,id,actor,expectedVersion+1);
        jdbc.update("INSERT INTO designacao_idempotencia(ator_id,operacao,chave,request_hash,designacao_id) VALUES (?,?,?,?,?)",actor,"ACCEPT:"+orderId,idem,hash,id);return assignment(id);
    }

    List<AssignmentView> own(UUID actor){return jdbc.query("SELECT d.id FROM designacao d JOIN entregador e ON e.id=d.entregador_id JOIN usuario u ON u.id=e.usuario_id WHERE e.usuario_id=? AND u.estado='ATIVO' ORDER BY d.aceita_em DESC,d.id",(rs,n)->assignment(rs.getObject(1,UUID.class)),actor);}
    AssignmentView get(UUID actor,UUID orderId){Map<String,Object> row=assignmentByOrder(orderId);if(!isPatientParticipant(actor,orderId)&&!actor.equals(row.get("usuario_id")))throw notFound();return assignment((UUID)row.get("id"));}
    OperationalIdentityView identity(UUID actor,UUID orderId){Map<String,Object> row=assignmentByOrder(orderId);if(!isPatientParticipant(actor,orderId))throw notFound();try{Map<?,?> value=json.readValue(String.valueOf(row.get("identificacao_snapshot")),Map.class);return new OperationalIdentityView(String.valueOf(value.get("displayName")),String.valueOf(value.get("plate")),String.valueOf(value.get("model")),String.valueOf(value.get("color")),UUID.fromString(String.valueOf(value.get("photoDocumentId"))));}catch(Exception ex){throw error(HttpStatus.INTERNAL_SERVER_ERROR,"DATA_UNAVAILABLE","Identificação operacional indisponível.");}}
    boolean assignedDriver(UUID actor,UUID orderId){return jdbc.queryForObject("SELECT count(*) FROM designacao d JOIN entregador e ON e.id=d.entregador_id WHERE d.pedido_id=? AND e.usuario_id=? AND d.encerrada_em IS NULL",Long.class,orderId,actor)==1;}

    private Map<String,Object> approvedDriver(UUID actor){try{return jdbc.queryForMap("SELECT e.*,u.nome_cifrado FROM entregador e JOIN usuario u ON u.id=e.usuario_id WHERE e.usuario_id=? AND e.estado='APROVADO' AND u.estado='ATIVO'",actor);}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.FORBIDDEN,"DRIVER_NOT_APPROVED","O entregador não está aprovado.");}}
    private Map<String,Object> approvedDriverForUpdate(UUID actor){try{return jdbc.queryForMap("SELECT e.*,u.nome_cifrado FROM entregador e JOIN usuario u ON u.id=e.usuario_id WHERE e.usuario_id=? AND e.estado='APROVADO' AND u.estado='ATIVO' FOR UPDATE OF e",actor);}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.FORBIDDEN,"DRIVER_NOT_APPROVED","O entregador não está aprovado.");}}
    private Map<String,Object> approvedLinkForUpdate(UUID actor,UUID link){try{return jdbc.queryForMap("SELECT vv.*,v.placa,v.modelo,v.cor,v.estado AS veiculo_estado FROM vinculo_veiculo vv JOIN veiculo v ON v.id=vv.veiculo_id JOIN entregador e ON e.id=vv.entregador_id WHERE vv.id=? AND e.usuario_id=? AND vv.estado='APROVADO' AND v.estado='APROVADO' AND (vv.valido_ate IS NULL OR vv.valido_ate>=current_date) FOR UPDATE OF vv",link,actor);}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.FORBIDDEN,"VEHICLE_NOT_APPROVED","O vínculo de veículo não está aprovado e vigente.");}}
    private Map<String,Object> orderForUpdate(UUID id){try{return jdbc.queryForMap("SELECT * FROM pedido WHERE id=? FOR UPDATE",id);}catch(EmptyResultDataAccessException ex){throw notFound();}}
    private Map<String,Object> currentQuote(UUID order){try{return jdbc.queryForMap("SELECT * FROM orcamento WHERE pedido_id=? AND estado='ACEITO' FOR UPDATE",order);}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.CONFLICT,"COVERAGE_NOT_CONFIRMED","O pedido não possui orçamento aceito atual.");}}
    private Map<String,Object> requireActivePolicy(){try{return jdbc.queryForMap("SELECT * FROM politica_designacao WHERE estado='ATIVA' AND inicio<=clock_timestamp() AND (fim IS NULL OR fim>clock_timestamp()) ORDER BY numero DESC LIMIT 1");}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","O limite de tarefas simultâneas não está definido.");}}
    private void requireProtocol(Map<String,Object> order){if(order.get("protocolo_id")==null||jdbc.queryForObject("SELECT count(*) FROM protocolo_custodia WHERE id=? AND unidade_id=? AND estado='HABILITADO' AND cobertura_retorno_confirmada AND inicio<=clock_timestamp() AND (fim IS NULL OR fim>clock_timestamp())",Long.class,order.get("protocolo_id"),order.get("unidade_id"))!=1)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","Unidade, protocolo e cobertura de retorno não estão habilitados.");}
    private void requireCoverage(UUID order,Map<String,Object> q){BigDecimal patient=(BigDecimal)q.get("paciente_valor"),subsidy=(BigDecimal)q.get("subsidio_valor");boolean paid=patient.signum()==0||jdbc.queryForObject("SELECT count(*) FROM operacao_financeira WHERE pedido_id=? AND tipo='COBRANCA' AND estado='CONFIRMADA'",Long.class,order)==1;boolean reserved=subsidy.signum()==0||jdbc.queryForObject("SELECT count(*) FROM reserva_subsidio WHERE orcamento_id=? AND estado='RESERVADA' AND valor-valor_liberado-valor_liquidado=?",Long.class,q.get("id"),subsidy)==1;if(!paid||!reserved)throw error(HttpStatus.CONFLICT,"COVERAGE_NOT_CONFIRMED","A cobertura não está efetivamente confirmada.");}
    private String identitySnapshot(Map<String,Object> driver,Map<String,Object> link){if(driver.get("foto_aprovada_id")==null)throw error(HttpStatus.FORBIDDEN,"DRIVER_NOT_APPROVED","A foto operacional aprovada é obrigatória.");try{return json.writeValueAsString(Map.of("displayName",protector.decrypt((byte[])driver.get("nome_cifrado")),"plate",link.get("placa"),"model",link.get("modelo"),"color",link.get("cor"),"photoDocumentId",driver.get("foto_aprovada_id")));}catch(JsonProcessingException ex){throw new IllegalStateException(ex);}}
    private String city(byte[] encrypted){try{return json.readValue(protector.decrypt(encrypted),Address.class).city();}catch(Exception ex){throw error(HttpStatus.INTERNAL_SERVER_ERROR,"DATA_UNAVAILABLE","Cidade da oferta indisponível.");}}
    private Map<String,Object> assignmentByOrder(UUID order){try{return jdbc.queryForMap("SELECT d.*,e.usuario_id FROM designacao d JOIN entregador e ON e.id=d.entregador_id WHERE d.pedido_id=? AND d.encerrada_em IS NULL",order);}catch(EmptyResultDataAccessException ex){throw notFound();}}
    private boolean isPatientParticipant(UUID actor,UUID order){long n=jdbc.queryForObject("SELECT count(*) FROM pedido p JOIN paciente pt ON pt.id=p.paciente_id LEFT JOIN autorizacao_paciente a ON a.paciente_id=p.paciente_id AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() LEFT JOIN autorizacao_escopo ae ON ae.autorizacao_id=a.id AND ae.escopo IN ('PEDIDOS','RECEBIMENTO') WHERE p.id=? AND (pt.usuario_id=? OR ae.autorizacao_id IS NOT NULL)",Long.class,actor,order,actor);return n>0;}
    private AssignmentView replay(UUID actor,UUID order,String key,byte[] hash){try{Map<String,Object> r=jdbc.queryForMap("SELECT request_hash,designacao_id FROM designacao_idempotencia WHERE ator_id=? AND operacao=? AND chave=?",actor,"ACCEPT:"+order,key);if(!MessageDigest.isEqual((byte[])r.get("request_hash"),hash))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","A chave foi usada com dados diferentes.");return assignment((UUID)r.get("designacao_id"));}catch(EmptyResultDataAccessException ex){return null;}}
    private AssignmentView assignment(UUID id){return jdbc.queryForObject("SELECT id,pedido_id,entregador_id,vinculo_id,encerrada_em IS NULL,aceita_em,encerrada_em,version FROM designacao WHERE id=?",(rs,n)->new AssignmentView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getBoolean(5)?"ATIVA":"ENCERRADA",rs.getTimestamp(6).toInstant(),rs.getTimestamp(7)==null?null:rs.getTimestamp(7).toInstant(),rs.getLong(8)),id);}
    private byte[] hash(Object value){try{return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private static OrderService.OrderException notFound(){return error(HttpStatus.NOT_FOUND,"NOT_FOUND","Tarefa não encontrada.");}
    private static OrderService.OrderException error(HttpStatus s,String c,String m){return OrderService.error(s,c,m);}
}
