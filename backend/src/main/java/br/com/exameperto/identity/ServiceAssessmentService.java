package br.com.exameperto.identity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
class ServiceAssessmentService {
    private final JdbcTemplate jdbc;
    private final MfaService mfa;
    private final ObjectMapper json;

    ServiceAssessmentService(JdbcTemplate jdbc, MfaService mfa, ObjectMapper json) {
        this.jdbc = jdbc; this.mfa = mfa; this.json = json;
    }

    @Transactional
    UUID ensureForCancellation(UUID orderId, UUID cancellationId, UUID actor) {
        Map<String,Object> row = assessmentContext(orderId);
        UUID existing = existing(orderId);
        if (existing != null) {
            jdbc.update("UPDATE apuracao_remuneracao SET solicitacao_cancelamento_id=coalesce(solicitacao_cancelamento_id,?),updated_at=clock_timestamp() WHERE id=? AND estado IN ('BLOQUEADA','PENDENTE_REVISAO','COMPLEMENTO_SOLICITADO')", cancellationId, existing);
            return existing;
        }
        UUID id=UUID.randomUUID(); String reason="Cancelamento após designação: apuração manual obrigatória.";
        jdbc.update("INSERT INTO apuracao_remuneracao(id,pedido_id,designacao_id,orcamento_id,solicitacao_cancelamento_id,modalidade,estado,moeda,responsavel_id,motivo,evidencias,proposta,updated_at) VALUES (?,?,?,?,?,'SERVICO_PARCIAL','PENDENTE_REVISAO',?,?,?,'[]'::jsonb,'{}'::jsonb,clock_timestamp())",id,orderId,row.get("designacao_id"),row.get("orcamento_id"),cancellationId,"BRL",actor,reason);
        audit(id,actor,"ABERTA",null,"PENDENTE_REVISAO",Map.of("source","CANCELAMENTO")); return id;
    }

    @Transactional
    UUID ensureForIncident(UUID orderId, UUID incidentId, UUID actor) {
        Map<String,Object> row = assessmentContext(orderId); UUID existing=existing(orderId);
        if(existing!=null){jdbc.update("UPDATE apuracao_remuneracao SET ocorrencia_id=coalesce(ocorrencia_id,?),updated_at=clock_timestamp() WHERE id=? AND estado IN ('BLOQUEADA','PENDENTE_REVISAO','COMPLEMENTO_SOLICITADO')",incidentId,existing);return existing;}
        UUID id=UUID.randomUUID();String reason="Ocorrência com serviço executado: apuração manual obrigatória.";
        jdbc.update("INSERT INTO apuracao_remuneracao(id,pedido_id,designacao_id,orcamento_id,ocorrencia_id,modalidade,estado,moeda,responsavel_id,motivo,evidencias,proposta,updated_at) VALUES (?,?,?,?,?,'OCORRENCIA','PENDENTE_REVISAO',?,?,?,'[]'::jsonb,'{}'::jsonb,clock_timestamp())",id,orderId,row.get("designacao_id"),row.get("orcamento_id"),incidentId,"BRL",actor,reason);
        audit(id,actor,"ABERTA",null,"PENDENTE_REVISAO",Map.of("source","OCORRENCIA")); return id;
    }

    List<ServiceAssessmentView> pending(AuthService.SessionPrincipal p) { mfa.requireVerified(p); if(jdbc.queryForObject("SELECT count(*) FROM papel_global WHERE usuario_id=? AND papel='GESTOR_FINANCEIRO' AND revogado_em IS NULL",Long.class,p.userId())==0 && jdbc.queryForObject("SELECT count(*) FROM membro_instituicao WHERE usuario_id=? AND papel='GESTOR_FINANCEIRO' AND revogado_em IS NULL",Long.class,p.userId())==0)throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","Gestor financeiro não autorizado."); return jdbc.query("SELECT a.id FROM apuracao_remuneracao a LEFT JOIN orcamento q ON q.id=a.orcamento_id LEFT JOIN programa pr ON pr.id=q.programa_id WHERE a.estado IN ('BLOQUEADA','PENDENTE_REVISAO','COMPLEMENTO_SOLICITADO') AND (pr.instituicao_id IS NULL OR EXISTS (SELECT 1 FROM membro_instituicao mi WHERE mi.instituicao_id=pr.instituicao_id AND mi.usuario_id=? AND mi.papel='GESTOR_FINANCEIRO' AND mi.revogado_em IS NULL) OR EXISTS (SELECT 1 FROM papel_global pg WHERE pg.usuario_id=? AND pg.papel='GESTOR_FINANCEIRO' AND pg.revogado_em IS NULL)) ORDER BY a.updated_at,a.id",(rs,n)->view(rs.getObject(1,UUID.class)),p.userId(),p.userId()); }
    ServiceAssessmentView get(AuthService.SessionPrincipal p,UUID id){Map<String,Object> row=lockless(id); authorizeRead(p,row); return view(id);}
    ServiceAssessmentView forOrder(UUID actor,UUID order){Map<String,Object> row;try{row=jdbc.queryForMap("SELECT * FROM apuracao_remuneracao WHERE pedido_id=?",order);}catch(EmptyResultDataAccessException ex){throw notFound();}if(!participant(actor,order)&&!driver(actor,order))throw notFound();return view((UUID)row.get("id"));}

    @Transactional
    ServiceAssessmentView propose(AuthService.SessionPrincipal p,UUID id,String key,ServiceAssessmentProposalInput input){
        requireKey(key); Map<String,Object> row=lock(id); if(!manager(p,(UUID)row.get("instituicao_id")))throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","Gestor financeiro não autorizado para a cobertura."); if(replayOrThrow(p.userId(),"PROPOSE",key,input,id))return view(id);
        if(!List.of("PENDENTE_REVISAO","COMPLEMENTO_SOLICITADO").contains(row.get("estado"))) throw error(HttpStatus.CONFLICT,"ASSESSMENT_NOT_EDITABLE","A apuração não está aguardando proposta.");
        amounts(input); Map<String,Object> quote=quote(row); BigDecimal total=input.driverAmount(); if(total.compareTo((BigDecimal)quote.get("frete"))>0)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"ADDITIONAL_COVERAGE_REQUIRED","O valor excede a cobertura aceita; autorização adicional é necessária e não foi criada automaticamente.");
        if(input.patientAmount().compareTo((BigDecimal)quote.get("paciente_valor"))>0||input.institutionAmount().compareTo((BigDecimal)quote.get("subsidio_valor"))>0)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"COVERAGE_EXCEEDED","A proposta excede a parcela de cobertura já aceita.");
        if(input.patientAmount().add(input.institutionAmount()).compareTo(total)!=0)throw error(HttpStatus.BAD_REQUEST,"ASSESSMENT_SPLIT_INVALID","As parcelas do paciente e da instituição devem somar o valor devido ao entregador.");
        String proposal=write(Map.of("driverAmount",input.driverAmount(),"patientAmount",input.patientAmount(),"institutionAmount",input.institutionAmount()));
        jdbc.update("UPDATE apuracao_remuneracao SET estado='PENDENTE_REVISAO',proposta=?::jsonb,motivo=?,evidencias=?::jsonb,valor_entregador=?,valor_paciente=?,valor_instituicao=?,versao=versao+1,updated_at=clock_timestamp() WHERE id=?",proposal,input.reason(),write(input.evidenceReferences()),input.driverAmount(),input.patientAmount(),input.institutionAmount(),id);
        jdbc.update("INSERT INTO apuracao_idempotencia(ator_id,operacao,chave,request_hash,apuracao_id) VALUES (?,?,?,?,?)",p.userId(),"PROPOSE",key,hash(input),id);audit(id,p.userId(),"PROPOSTA",String.valueOf(row.get("estado")),"PENDENTE_REVISAO",Map.of("amount",input.driverAmount()));return view(id);
    }

    @Transactional
    ServiceAssessmentView review(AuthService.SessionPrincipal p,UUID id,String key,ServiceAssessmentReviewInput input){
        requireKey(key); Map<String,Object> row=lock(id); if(!manager(p,(UUID)row.get("instituicao_id")))throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","Gestor financeiro não autorizado para a cobertura."); if(replayOrThrow(p.userId(),"REVIEW",key,input,id))return view(id);
        if(!"PENDENTE_REVISAO".equals(row.get("estado")))throw error(HttpStatus.CONFLICT,"ASSESSMENT_NOT_REVIEWABLE","A apuração não aguarda revisão.");
        if(row.get("responsavel_id")==null)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"ASSESSMENT_RESPONSIBLE_REQUIRED","A apuração precisa de responsável pela cobertura.");
        if(p.userId().equals(row.get("responsavel_id")))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"SEPARATION_REQUIRED","A revisão deve ser feita por pessoa distinta do responsável.");
        String decision=input.decision().trim().toUpperCase();if(!List.of("APROVAR","REJEITAR","COMPLEMENTAR").contains(decision))throw error(HttpStatus.BAD_REQUEST,"INVALID_INPUT","Decisão de apuração inválida.");
        if("COMPLEMENTAR".equals(decision)){jdbc.update("UPDATE apuracao_remuneracao SET estado='COMPLEMENTO_SOLICITADO',revisor_id=?,motivo=?,versao=versao+1,updated_at=clock_timestamp() WHERE id=?",p.userId(),input.reason(),id);audit(id,p.userId(),"COMPLEMENTO",String.valueOf(row.get("estado")),"COMPLEMENTO_SOLICITADO",Map.of("reason",input.reason()));remember(p.userId(),"REVIEW",key,input,id);return view(id);}
        if("REJEITAR".equals(decision)){jdbc.update("UPDATE apuracao_remuneracao SET estado='REJEITADA',revisor_id=?,motivo=?,versao=versao+1,updated_at=clock_timestamp() WHERE id=?",p.userId(),input.reason(),id);audit(id,p.userId(),"REJEITADA",String.valueOf(row.get("estado")),"REJEITADA",Map.of("reason",input.reason()));remember(p.userId(),"REVIEW",key,input,id);return view(id);}
        approve(p,id,row,input);remember(p.userId(),"REVIEW",key,input,id);return view(id);
    }

    private void approve(AuthService.SessionPrincipal p,UUID id,Map<String,Object> row,ServiceAssessmentReviewInput input){
        Map<String,Object> quote=quote(row);Map<String,Object> proposal=readMap(String.valueOf(row.get("proposta")));BigDecimal due=decimal(proposal,"driverAmount"),patient=decimal(proposal,"patientAmount"),institution=decimal(proposal,"institutionAmount");
        if(due.compareTo((BigDecimal)quote.get("frete"))>0)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"ADDITIONAL_COVERAGE_REQUIRED","A cobertura adicional não foi autorizada.");
        UUID program=(UUID)quote.get("programa_id");if(institution.signum()>0&&(program==null||jdbc.queryForObject("SELECT count(*) FROM conta_programa WHERE programa_id=?",Long.class,program)!=1))throw error(HttpStatus.CONFLICT,"COVERAGE_NOT_CONFIRMED","A cobertura institucional não está disponível.");
        BigDecimal release=BigDecimal.ZERO;if(program!=null&&institution.signum()>0){Map<String,Object> reserve=jdbc.queryForMap("SELECT * FROM reserva_subsidio WHERE orcamento_id=? FOR UPDATE",row.get("orcamento_id"));BigDecimal remaining=((BigDecimal)reserve.get("valor")).subtract((BigDecimal)reserve.get("valor_liquidado")).subtract((BigDecimal)reserve.get("valor_liberado"));if(institution.compareTo(remaining)>0)throw error(HttpStatus.CONFLICT,"COVERAGE_NOT_CONFIRMED","A reserva não cobre a parcela institucional proposta.");release=remaining.subtract(institution);jdbc.update("UPDATE reserva_subsidio SET valor_liquidado=valor_liquidado+?,valor_liberado=valor_liberado+?,estado=CASE WHEN valor_liquidado+?+valor_liberado+?=valor THEN 'ENCERRADA' WHEN valor_liquidado+?=valor THEN 'LIQUIDADA' ELSE 'PARCIAL' END WHERE id=?",institution,release,institution,release,institution,reserve.get("id"));jdbc.update("UPDATE conta_programa SET disponivel=disponivel+?,reservado=reservado-?,liquidado=liquidado+?,version=version+1 WHERE programa_id=? AND reservado>=?",release,remaining,institution,program,remaining);}
        BigDecimal acceptedPatient=(BigDecimal)quote.get("paciente_valor"),refund=acceptedPatient.subtract(patient);if(refund.signum()<0)refund=BigDecimal.ZERO;
        jdbc.update("UPDATE apuracao_remuneracao SET estado='APROVADA',revisor_id=?,valor_devido=?,valor_entregador=?,valor_paciente=?,valor_instituicao=?,saldo_liberar=?,saldo_devolver=?,regra_codigo='APURACAO_MANUAL_SERVICO_COMPROVADO',concluida_em=clock_timestamp(),versao=versao+1,updated_at=clock_timestamp() WHERE id=?",p.userId(),due,due,patient,institution,release,refund,id);
        if(release.signum()>0)operation(row,"LIBERACAO",release,program,"liberacao:apuracao:"+id,"CONFIRMADA");
        if(due.signum()>0){UUID op=operation(row,"LIQUIDACAO",due,program,"liquidacao:apuracao:"+id,"CONFIRMADA");short seq=1;if(patient.signum()>0)entry(op,seq++,"PACIENTE_A_PAGAR","DEBITO",patient,row);if(institution.signum()>0)entry(op,seq++,"SUBSIDIO_A_PAGAR","DEBITO",institution,row);entry(op,seq,"ENTREGADOR_A_PAGAR","CREDITO",due,row);createObligation(row,op,due);}
        if(refund.signum()>0&&jdbc.queryForObject("SELECT count(*) FROM operacao_financeira WHERE pedido_id=? AND tipo='COBRANCA' AND estado='CONFIRMADA'",Long.class,row.get("pedido_id"))>0)operation(row,"ESTORNO",refund,null,"estorno:apuracao:"+id,"PENDENTE");
        if(row.get("solicitacao_cancelamento_id")!=null)jdbc.update("UPDATE solicitacao_cancelamento SET estado='APROVADA',tratamento_financeiro=CASE WHEN ? > 0 THEN 'ESTORNO_SOLICITADO' ELSE tratamento_financeiro END,updated_at=clock_timestamp() WHERE id=?",refund,row.get("solicitacao_cancelamento_id"));
        audit(id,p.userId(),"APROVADA","PENDENTE_REVISAO","APROVADA",Map.of("reason",input.reason(),"refundPending",refund));
    }

    private UUID operation(Map<String,Object> row,String type,BigDecimal amount,UUID program,String key,String state){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO operacao_financeira(id,pedido_id,programa_id,tipo,chave_negocio,valor,moeda,estado) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT (chave_negocio) DO NOTHING",id,row.get("pedido_id"),program,type,key,amount,"BRL",state);return jdbc.queryForObject("SELECT id FROM operacao_financeira WHERE chave_negocio=?",UUID.class,key);}
    private void entry(UUID op,short seq,String account,String direction,BigDecimal amount,Map<String,Object> row){jdbc.update("INSERT INTO lancamento_financeiro(operacao_id,sequencia,conta_codigo,sentido,valor,moeda,pedido_id) VALUES (?,?,?,?,?,'BRL',?)",op,seq,account,direction,amount,row.get("pedido_id"));}
    private void createObligation(Map<String,Object> row,UUID op,BigDecimal amount){try{jdbc.queryForObject("SELECT id FROM repasse WHERE pedido_id=?",UUID.class,row.get("pedido_id"));return;}catch(EmptyResultDataAccessException ignored){}UUID driver=jdbc.queryForObject("SELECT entregador_id FROM designacao WHERE id=?",UUID.class,row.get("designacao_id"));UUID institution=row.get("instituicao_id")==null?null: (UUID)row.get("instituicao_id");jdbc.update("INSERT INTO repasse(id,obrigacao_operacao_id,pedido_id,entregador_id,instituicao_id,referencia,valor,moeda,destinatario_referencia,estado) VALUES (?,?,?,?,?,?,?,'BRL',?,'OBRIGACAO_REGISTRADA')",UUID.randomUUID(),op,row.get("pedido_id"),driver,institution,"repasse:apuracao:"+row.get("id"),amount,"entregador:"+driver);}

    private Map<String,Object> assessmentContext(UUID order){try{return jdbc.queryForMap("SELECT d.id designacao_id,q.id orcamento_id,q.frete,q.paciente_valor,q.subsidio_valor,q.programa_id,pr.instituicao_id FROM pedido p JOIN designacao d ON d.pedido_id=p.id LEFT JOIN orcamento q ON q.pedido_id=p.id AND q.estado='ACEITO' LEFT JOIN programa pr ON pr.id=q.programa_id WHERE p.id=? AND d.encerrada_em IS NULL ORDER BY d.aceita_em DESC LIMIT 1",order);}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.CONFLICT,"ASSESSMENT_CONTEXT_MISSING","Pedido sem designação e orçamento aceito para apuração.");}}
    private Map<String,Object> quote(Map<String,Object> row){try{return jdbc.queryForMap("SELECT q.* FROM orcamento q WHERE q.id=?",row.get("orcamento_id"));}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.CONFLICT,"ASSESSMENT_CONTEXT_MISSING","Orçamento aceito não encontrado.");}}
    private UUID existing(UUID order){try{return jdbc.queryForObject("SELECT id FROM apuracao_remuneracao WHERE pedido_id=?",UUID.class,order);}catch(EmptyResultDataAccessException ex){return null;}}
    private Map<String,Object> lock(UUID id){try{Map<String,Object> row=jdbc.queryForMap("SELECT * FROM apuracao_remuneracao WHERE id=? FOR UPDATE",id);try{row.put("instituicao_id",jdbc.queryForObject("SELECT pr.instituicao_id FROM programa pr JOIN orcamento q ON q.programa_id=pr.id WHERE q.id=?",UUID.class,row.get("orcamento_id")));}catch(EmptyResultDataAccessException ignored){row.put("instituicao_id",null);}return row;}catch(EmptyResultDataAccessException ex){throw notFound();}}
    private Map<String,Object> lockless(UUID id){try{return jdbc.queryForMap("SELECT a.*,pr.instituicao_id FROM apuracao_remuneracao a LEFT JOIN orcamento q ON q.id=a.orcamento_id LEFT JOIN programa pr ON pr.id=q.programa_id WHERE a.id=?",id);}catch(EmptyResultDataAccessException ex){throw notFound();}}
    private void authorizeRead(AuthService.SessionPrincipal p,Map<String,Object> row){if(isManager(p.userId(),(UUID)row.get("instituicao_id"))){mfa.requireVerified(p);return;}if(!participant(p.userId(),(UUID)row.get("pedido_id"))&&!driver(p.userId(),(UUID)row.get("pedido_id")))throw notFound();}
    private boolean manager(AuthService.SessionPrincipal p,UUID institution){mfa.requireVerified(p);if(institution!=null)return jdbc.queryForObject("SELECT count(*) FROM membro_instituicao mi JOIN papel_global pg ON pg.usuario_id=mi.usuario_id AND pg.papel='GESTOR_FINANCEIRO' AND pg.revogado_em IS NULL WHERE mi.usuario_id=? AND mi.instituicao_id=? AND mi.papel='GESTOR_FINANCEIRO'",Long.class,p.userId(),institution)==1;return jdbc.queryForObject("SELECT count(*) FROM papel_global WHERE usuario_id=? AND papel='GESTOR_FINANCEIRO' AND revogado_em IS NULL",Long.class,p.userId())==1;}
    private boolean isManager(UUID actor,UUID institution){if(institution!=null)return jdbc.queryForObject("SELECT count(*) FROM membro_instituicao mi JOIN papel_global pg ON pg.usuario_id=mi.usuario_id AND pg.papel='GESTOR_FINANCEIRO' AND pg.revogado_em IS NULL WHERE mi.usuario_id=? AND mi.instituicao_id=? AND mi.papel='GESTOR_FINANCEIRO'",Long.class,actor,institution)==1;return jdbc.queryForObject("SELECT count(*) FROM papel_global WHERE usuario_id=? AND papel='GESTOR_FINANCEIRO' AND revogado_em IS NULL",Long.class,actor)==1;}
    private boolean participant(UUID actor,UUID order){return jdbc.queryForObject("SELECT count(*) FROM pedido p JOIN paciente pa ON pa.id=p.paciente_id WHERE p.id=? AND (pa.usuario_id=? OR p.destinatario_id=?)",Long.class,order,actor,actor)>0;}
    private boolean driver(UUID actor,UUID order){return jdbc.queryForObject("SELECT count(*) FROM designacao d JOIN entregador e ON e.id=d.entregador_id WHERE d.pedido_id=? AND e.usuario_id=?",Long.class,order,actor)>0;}
    private ServiceAssessmentView view(UUID id){return jdbc.queryForObject("SELECT id,pedido_id,ocorrencia_id,solicitacao_cancelamento_id,modalidade,estado,responsavel_id,revisor_id,valor_entregador,valor_paciente,valor_instituicao,saldo_liberar,saldo_devolver,motivo,evidencias::text,versao,updated_at,proposta<>'{}'::jsonb FROM apuracao_remuneracao WHERE id=?",(rs,n)->new ServiceAssessmentView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getString(5),rs.getString(6),rs.getObject(7,UUID.class),rs.getObject(8,UUID.class),rs.getBigDecimal(9),rs.getBigDecimal(10),rs.getBigDecimal(11),rs.getBigDecimal(12),rs.getBigDecimal(13),rs.getString(14),readList(rs.getString(15)),rs.getLong(16),rs.getTimestamp(17).toInstant(),rs.getBoolean(18)),id);}
    private List<String> readList(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception ex){return List.of();}}
    private Map<String,Object> readMap(String value){try{return json.readValue(value,new TypeReference<>(){});}catch(Exception ex){throw error(HttpStatus.CONFLICT,"ASSESSMENT_PROPOSAL_INVALID","A proposta persistida está inválida.");}}
    private BigDecimal decimal(Map<String,Object> map,String key){return new BigDecimal(String.valueOf(map.get(key)));}
    private void amounts(ServiceAssessmentProposalInput in){if(in.driverAmount().scale()>2||in.patientAmount().scale()>2||in.institutionAmount().scale()>2)throw error(HttpStatus.BAD_REQUEST,"MONEY_SCALE_INVALID","Valores devem usar centavos.");}
    private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception ex){throw new IllegalStateException(ex);}}
    private boolean replayOrThrow(UUID actor,String operation,String key,Object input,UUID id){try{Map<String,Object> row=jdbc.queryForMap("SELECT request_hash,apuracao_id FROM apuracao_idempotencia WHERE ator_id=? AND operacao=? AND chave=?",actor,operation,key);if(!MessageDigest.isEqual((byte[])row.get("request_hash"),hash(input))||!id.equals(row.get("apuracao_id")))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","A chave de idempotência já foi usada com dados diferentes.");return true;}catch(EmptyResultDataAccessException ignored){return false;}}
    private void remember(UUID actor,String operation,String key,Object input,UUID id){jdbc.update("INSERT INTO apuracao_idempotencia(ator_id,operacao,chave,request_hash,apuracao_id) VALUES (?,?,?,?,?) ON CONFLICT DO NOTHING",actor,operation,key,hash(input),id);}
    private byte[] hash(Object value){try{return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private void audit(UUID id,UUID actor,String action,String prior,String next,Object detail){jdbc.update("INSERT INTO auditoria_apuracao(id,apuracao_id,ator_id,acao,estado_anterior,estado_novo,detalhe,correlacao_id) VALUES (?,?,?,?,?,?,?::jsonb,?)",UUID.randomUUID(),id,actor,action,prior,next,write(detail),UUID.randomUUID());}
    private void requireKey(String key){if(key==null||key.isBlank())throw error(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_REQUIRED","A operação exige Idempotency-Key.");}
    private static OrderService.OrderException notFound(){return error(HttpStatus.NOT_FOUND,"NOT_FOUND","Apuração não encontrada.");}
    private static OrderService.OrderException error(HttpStatus status,String code,String message){return OrderService.error(status,code,message);}
}
