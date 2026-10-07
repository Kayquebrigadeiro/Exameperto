package br.com.exameperto.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PayoutService {
    private final JdbcTemplate jdbc; private final MfaService mfa; private final PayoutProvider provider; private final ObjectMapper json;
    PayoutService(JdbcTemplate jdbc,MfaService mfa,PayoutProvider provider,ObjectMapper json){this.jdbc=jdbc;this.mfa=mfa;this.provider=provider;this.json=json;}

    void recordCompletedService(UUID orderId) {
        Map<String,Object> row=jdbc.queryForMap("SELECT p.id,o.id orcamento_id,o.frete,o.paciente_valor,o.subsidio_valor,o.moeda,o.programa_id,d.id designacao_id,d.entregador_id,e.usuario_id FROM pedido p JOIN orcamento o ON o.pedido_id=p.id AND o.estado='ACEITO' JOIN designacao d ON d.pedido_id=p.id JOIN entregador e ON e.id=d.entregador_id WHERE p.id=? ORDER BY d.aceita_em DESC LIMIT 1",orderId);
        UUID assessment=UUID.randomUUID(), obligation=UUID.randomUUID(), payout=UUID.randomUUID();
        BigDecimal total=(BigDecimal)row.get("frete"), patient=(BigDecimal)row.get("paciente_valor"), subsidy=(BigDecimal)row.get("subsidio_valor"); String currency=(String)row.get("moeda");
        jdbc.update("INSERT INTO apuracao_remuneracao(id,pedido_id,designacao_id,orcamento_id,modalidade,estado,regra_codigo,valor_devido,paciente_valor,subsidio_valor,moeda,concluida_em) VALUES (?,?,?,?,'SERVICO_COMPLETO','CONCLUIDA','FRETE_ACEITO_SERVICO_COMPLETO',?,?,?,?,clock_timestamp())",assessment,orderId,row.get("designacao_id"),row.get("orcamento_id"),total,patient,subsidy,currency);
        jdbc.update("INSERT INTO operacao_financeira(id,pedido_id,programa_id,tipo,chave_negocio,valor,moeda,estado,destinatario_usuario_id) VALUES (?,?,?,'LIQUIDACAO',?,?,?,'CONFIRMADA',?)",obligation,orderId,row.get("programa_id"),"servico:"+orderId,total,currency,row.get("usuario_id"));
        short seq=1;
        if(patient.signum()>0)jdbc.update("INSERT INTO lancamento_financeiro(operacao_id,sequencia,conta_codigo,sentido,valor,moeda,pedido_id) VALUES (?,?,'PACIENTE_A_PAGAR','DEBITO',?,?,?)",obligation,seq++,patient,currency,orderId);
        if(subsidy.signum()>0)jdbc.update("INSERT INTO lancamento_financeiro(operacao_id,sequencia,conta_codigo,sentido,valor,moeda,pedido_id) VALUES (?,?,'SUBSIDIO_A_PAGAR','DEBITO',?,?,?)",obligation,seq++,subsidy,currency,orderId);
        jdbc.update("INSERT INTO lancamento_financeiro(operacao_id,sequencia,conta_codigo,sentido,valor,moeda,pedido_id) VALUES (?,?,'ENTREGADOR_A_PAGAR','CREDITO',?,?,?)",obligation,seq,total,currency,orderId);
        if(subsidy.signum()>0){int changed=jdbc.update("UPDATE reserva_subsidio SET valor_liquidado=valor,estado='LIQUIDADA' WHERE orcamento_id=? AND estado='RESERVADA' AND valor=? AND moeda=?",row.get("orcamento_id"),subsidy,currency);if(changed!=1)throw error(HttpStatus.CONFLICT,"COVERAGE_MISMATCH","A cobertura reservada não pôde ser liquidada.");jdbc.update("UPDATE conta_programa SET reservado=reservado-?,liquidado=liquidado+?,version=version+1 WHERE programa_id=? AND reservado>=?",subsidy,subsidy,row.get("programa_id"),subsidy);}
        UUID institution=row.get("programa_id")==null?null:jdbc.queryForObject("SELECT instituicao_id FROM programa WHERE id=?",UUID.class,row.get("programa_id"));
        String reference="repasse:"+orderId, recipient="entregador:"+row.get("entregador_id");
        jdbc.update("INSERT INTO repasse(id,obrigacao_operacao_id,pedido_id,entregador_id,instituicao_id,referencia,valor,moeda,destinatario_referencia,estado) VALUES (?,?,?,?,?,?,?,?,?,'OBRIGACAO_REGISTRADA')",payout,obligation,orderId,row.get("entregador_id"),institution,reference,total,currency,recipient);
        audit(payout,null,"OBRIGACAO_REGISTRADA",null,"OBRIGACAO_REGISTRADA",null);
    }

    @Transactional
    PayoutView request(AuthService.SessionPrincipal principal,UUID payoutId,String key) {
        if(!provider.configured())throw error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","O provedor de repasse não está habilitado.");
        requireKey(key);mfa.requireVerified(principal);Map<String,Object> row=lock(payoutId);requireManager(principal.userId(),(UUID)row.get("instituicao_id"));
        byte[] hash=hash("REQUEST:"+payoutId);PayoutView replay=replay(principal.userId(),"REQUEST",key,hash);if(replay!=null)return replay;
        if(!"OBRIGACAO_REGISTRADA".equals(row.get("estado")))throw error(HttpStatus.CONFLICT,"PAYOUT_ALREADY_REQUESTED","O repasse já foi solicitado; consulte e concilie a mesma referência.");
        UUID operation=UUID.randomUUID();
        jdbc.update("INSERT INTO operacao_financeira(id,pedido_id,tipo,chave_negocio,valor,moeda,estado,provedor,referencia_externa,destinatario_usuario_id) SELECT ?,pedido_id,'REPASSE',referencia,valor,moeda,'PROCESSANDO',?,?,e.usuario_id FROM repasse r JOIN entregador e ON e.id=r.entregador_id WHERE r.id=?",operation,provider.providerId(),row.get("referencia"),payoutId);
        jdbc.update("UPDATE repasse SET operacao_id=?,estado='SOLICITADO',solicitado_em=clock_timestamp(),updated_at=clock_timestamp(),version=version+1 WHERE id=?",operation,payoutId);
        jdbc.update("INSERT INTO financeiro_outbox(id,operacao_id,pedido_id,tipo,chave,payload_saneado,estado) VALUES (?,?,?,'SOLICITAR_REPASSE',?,jsonb_build_object('repasseId',?::text),'PENDENTE')",UUID.randomUUID(),operation,row.get("pedido_id"),"solicitar:"+row.get("referencia"),payoutId.toString());
        jdbc.update("INSERT INTO repasse_idempotencia(ator_id,operacao,chave,request_hash,repasse_id) VALUES (?,'REQUEST',?,?,?)",principal.userId(),key,hash,payoutId);
        audit(payoutId,principal.userId(),"SOLICITAR","OBRIGACAO_REGISTRADA","SOLICITADO",null);return view(payoutId);
    }

    @Transactional
    PayoutView reconcile(AuthService.SessionPrincipal principal,UUID payoutId,String key) {
        if(!provider.configured())throw error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","O provedor de repasse não está habilitado.");
        requireKey(key);mfa.requireVerified(principal);Map<String,Object> row=lock(payoutId);requireManager(principal.userId(),(UUID)row.get("instituicao_id"));
        byte[] hash=hash("RECONCILE:"+payoutId);PayoutView replay=replay(principal.userId(),"RECONCILE",key,hash);if(replay!=null)return replay;
        if(!List.of("SOLICITADO","INCERTO","DIVERGENTE").contains(row.get("estado")))throw error(HttpStatus.CONFLICT,"RECONCILIATION_NOT_ALLOWED","Este repasse não requer conciliação.");
        jdbc.update("UPDATE financeiro_outbox SET tipo='RECONCILIAR_REPASSE',estado='PENDENTE',disponivel_em=clock_timestamp() WHERE operacao_id=?",row.get("operacao_id"));
        jdbc.update("INSERT INTO repasse_idempotencia(ator_id,operacao,chave,request_hash,repasse_id) VALUES (?,'RECONCILE',?,?,?)",principal.userId(),key,hash,payoutId);
        audit(payoutId,principal.userId(),"RECONCILIAR",String.valueOf(row.get("estado")),String.valueOf(row.get("estado")),null);return view(payoutId);
    }

    @Transactional
    void applyOutcome(UUID payoutId,PayoutProvider.Outcome outcome,byte[] rawHash) {
        Map<String,Object> row=lock(payoutId);String providerId=provider.providerId();
        List<Map<String,Object>> existing=jdbc.queryForList("SELECT payload_hash FROM evento_repasse WHERE provedor=? AND evento_externo_id=?",providerId,outcome.eventId());
        if(!existing.isEmpty()){if(!MessageDigest.isEqual((byte[])existing.getFirst().get("payload_hash"),rawHash))markDivergent(row,"EVENT_HASH_MISMATCH");return;}
        String mismatch=null;if(!row.get("referencia").equals(outcome.reference()))mismatch="REFERENCE_MISMATCH";else if(outcome.amount()==null||((BigDecimal)row.get("valor")).compareTo(outcome.amount())!=0)mismatch="AMOUNT_MISMATCH";else if(!row.get("moeda").equals(outcome.currency()))mismatch="CURRENCY_MISMATCH";else if(!row.get("destinatario_referencia").equals(outcome.recipientReference()))mismatch="RECIPIENT_MISMATCH";
        String next=switch(outcome.result()){case CONFIRMED->"CONFIRMADO";case FAILED->"FALHOU";case ACCEPTED->"SOLICITADO";case UNCERTAIN->"INCERTO";};
        Timestamp latest=jdbc.queryForObject("SELECT max(ocorrido_em) FROM evento_repasse WHERE repasse_id=?",Timestamp.class,payoutId);
        boolean outOfOrder="CONFIRMADO".equals(row.get("estado"))||(latest!=null&&outcome.occurredAt().isBefore(latest.toInstant()));
        String eventResult=outOfOrder?"IGNORADO_FORA_ORDEM":mismatch!=null?"DIVERGENTE":next;
        jdbc.update("INSERT INTO evento_repasse(id,repasse_id,provedor,evento_externo_id,payload_hash,referencia,resultado,valor,moeda,destinatario_referencia,ocorrido_em) VALUES (?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),payoutId,providerId,outcome.eventId(),rawHash,outcome.reference(),eventResult,outcome.amount(),outcome.currency(),outcome.recipientReference(),Timestamp.from(outcome.occurredAt()));
        if(outOfOrder)return;
        if(mismatch!=null){markDivergent(row,mismatch);return;}
        transition(row,next,null);
        if("CONFIRMADO".equals(next)){UUID operation=(UUID)row.get("operacao_id");short seq=1;jdbc.update("INSERT INTO lancamento_financeiro(operacao_id,sequencia,conta_codigo,sentido,valor,moeda,pedido_id) VALUES (?,?,'ENTREGADOR_A_PAGAR','DEBITO',?,?,?)",operation,seq++ ,row.get("valor"),row.get("moeda"),row.get("pedido_id"));jdbc.update("INSERT INTO lancamento_financeiro(operacao_id,sequencia,conta_codigo,sentido,valor,moeda,pedido_id) VALUES (?,?,'CAIXA_REPASSE','CREDITO',?,?,?)",operation,seq,row.get("valor"),row.get("moeda"),row.get("pedido_id"));jdbc.update("UPDATE operacao_financeira SET estado='CONFIRMADA',version=version+1,updated_at=clock_timestamp() WHERE id=?",operation);jdbc.update("UPDATE financeiro_outbox SET estado='ENVIADO' WHERE operacao_id=?",operation);}else if("FALHOU".equals(next))jdbc.update("UPDATE operacao_financeira SET estado='FALHOU',version=version+1,updated_at=clock_timestamp() WHERE id=?",row.get("operacao_id"));else if("INCERTO".equals(next)){jdbc.update("UPDATE operacao_financeira SET estado='INCERTA',version=version+1,updated_at=clock_timestamp() WHERE id=?",row.get("operacao_id"));jdbc.update("UPDATE financeiro_outbox SET estado='RECONCILIAR' WHERE operacao_id=?",row.get("operacao_id"));}
    }

    PayoutPage panel(AuthService.SessionPrincipal principal){mfa.requireVerified(principal);UUID institution=managerInstitution(principal.userId());List<PayoutView> rows=jdbc.query("SELECT id FROM repasse WHERE instituicao_id=? ORDER BY created_at DESC,id DESC",(rs,n)->view(rs.getObject(1,UUID.class)),institution);return page(rows);}
    PayoutView get(AuthService.SessionPrincipal principal,UUID id){Map<String,Object> row=find(id);UUID actor=principal.userId();boolean driver=jdbc.queryForObject("SELECT count(*) FROM entregador WHERE id=? AND usuario_id=?",Long.class,row.get("entregador_id"),actor)==1;if(!driver){mfa.requireVerified(principal);requireManager(actor,(UUID)row.get("instituicao_id"));}return view(id);}
    PayoutView forOrder(UUID actor,UUID order){UUID id;try{id=jdbc.queryForObject("SELECT r.id FROM repasse r JOIN entregador e ON e.id=r.entregador_id JOIN pedido p ON p.id=r.pedido_id JOIN paciente pa ON pa.id=p.paciente_id WHERE r.pedido_id=? AND (e.usuario_id=? OR pa.usuario_id=?)",UUID.class,order,actor,actor);}catch(EmptyResultDataAccessException ex){throw notFound();}return view(id);}
    List<FinancialOperationView> financialForOrder(UUID actor,UUID order){
        boolean patient=jdbc.queryForObject("SELECT count(*) FROM pedido p JOIN paciente pa ON pa.id=p.paciente_id WHERE p.id=? AND pa.usuario_id=?",Long.class,order,actor)==1;
        boolean driver=jdbc.queryForObject("SELECT count(*) FROM repasse r JOIN entregador e ON e.id=r.entregador_id WHERE r.pedido_id=? AND e.usuario_id=?",Long.class,order,actor)==1;
        if(!patient&&!driver)throw notFound();
        String types=driver?"('REPASSE')":"('COBRANCA','ESTORNO')";
        return jdbc.query("SELECT id,pedido_id,tipo,valor,moeda,estado,version FROM operacao_financeira WHERE pedido_id=? AND tipo IN "+types+" ORDER BY created_at,id",(rs,n)->new FinancialOperationView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3),rs.getBigDecimal(4),rs.getString(5),rs.getString(6),rs.getLong(7)),order);
    }
    FinancialOperationView financial(UUID actor,UUID id){Map<String,Object> row;try{row=jdbc.queryForMap("SELECT o.*,r.entregador_id,p.paciente_id,pa.usuario_id paciente_usuario,e.usuario_id driver_usuario FROM operacao_financeira o JOIN pedido p ON p.id=o.pedido_id JOIN paciente pa ON pa.id=p.paciente_id LEFT JOIN repasse r ON r.operacao_id=o.id LEFT JOIN entregador e ON e.id=r.entregador_id WHERE o.id=?",id);}catch(EmptyResultDataAccessException ex){throw notFound();}if("REPASSE".equals(row.get("tipo"))&&!actor.equals(row.get("driver_usuario")))throw notFound();if(List.of("COBRANCA","ESTORNO").contains(row.get("tipo"))&&!actor.equals(row.get("paciente_usuario")))throw notFound();return new FinancialOperationView((UUID)row.get("id"),(UUID)row.get("pedido_id"),(String)row.get("tipo"),(BigDecimal)row.get("valor"),(String)row.get("moeda"),(String)row.get("estado"),((Number)row.get("version")).longValue());}
    PayoutProvider provider(){return provider;}
    PayoutView getForSystem(UUID id){return view(id);}
    Map<String,Object> claim(UUID id){Map<String,Object> row=lock(id);if(!List.of("SOLICITADO","INCERTO","DIVERGENTE").contains(row.get("estado")))throw error(HttpStatus.CONFLICT,"PAYOUT_FINAL","O repasse não pode ser processado novamente.");String outbox=jdbc.queryForObject("SELECT estado FROM financeiro_outbox WHERE operacao_id=? FOR UPDATE",String.class,row.get("operacao_id"));if("EM_ENVIO".equals(outbox))throw error(HttpStatus.CONFLICT,"PAYOUT_IN_PROGRESS","A mesma referência já está em processamento.");jdbc.update("UPDATE financeiro_outbox SET estado='EM_ENVIO',tentativas=tentativas+1 WHERE operacao_id=?",row.get("operacao_id"));return row;}

    private PayoutPage page(List<PayoutView> rows){return new PayoutPage(rows,rows.stream().filter(v->v.status().equals("OBRIGACAO_REGISTRADA")).count(),rows.stream().filter(v->v.status().equals("DIVERGENTE")).count(),rows.stream().filter(v->v.status().equals("INCERTO")).count());}
    private void markDivergent(Map<String,Object> row,String reason){transition(row,"DIVERGENTE",reason);jdbc.update("UPDATE operacao_financeira SET estado='RECONCILIAR',version=version+1,updated_at=clock_timestamp() WHERE id=?",row.get("operacao_id"));jdbc.update("UPDATE financeiro_outbox SET estado='RECONCILIAR' WHERE operacao_id=?",row.get("operacao_id"));}
    private void transition(Map<String,Object> row,String next,String reason){String prior=String.valueOf(row.get("estado"));jdbc.update("UPDATE repasse SET estado=?,divergencia_codigo=?,confirmado_em=CASE WHEN ?='CONFIRMADO' THEN clock_timestamp() ELSE confirmado_em END,updated_at=clock_timestamp(),version=version+1 WHERE id=?",next,reason,next,row.get("id"));audit((UUID)row.get("id"),null,"EVENTO_PROVEDOR",prior,next,reason);}
    private void audit(UUID id,UUID actor,String action,String prior,String next,String reason){jdbc.update("INSERT INTO auditoria_repasse(id,repasse_id,ator_id,acao,estado_anterior,estado_novo,motivo_codigo,correlacao_id) VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID(),id,actor,action,prior,next,reason,UUID.randomUUID());}
    private void requireManager(UUID actor,UUID institution){if(institution==null||jdbc.queryForObject("SELECT count(*) FROM membro_instituicao mi JOIN papel_global pg ON pg.usuario_id=mi.usuario_id AND pg.papel='GESTOR_FINANCEIRO' AND pg.revogado_em IS NULL WHERE mi.instituicao_id=? AND mi.usuario_id=? AND mi.papel='GESTOR_FINANCEIRO' AND mi.revogado_em IS NULL",Long.class,institution,actor)!=1)throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","Gestor financeiro não autorizado para esta instituição.");}
    private UUID managerInstitution(UUID actor){List<UUID> ids=jdbc.query("SELECT mi.instituicao_id FROM membro_instituicao mi JOIN papel_global pg ON pg.usuario_id=mi.usuario_id AND pg.papel='GESTOR_FINANCEIRO' AND pg.revogado_em IS NULL WHERE mi.usuario_id=? AND mi.papel='GESTOR_FINANCEIRO' AND mi.revogado_em IS NULL",(rs,n)->rs.getObject(1,UUID.class),actor);if(ids.size()!=1)throw error(HttpStatus.FORBIDDEN,"FORBIDDEN","Gestor financeiro não autorizado.");return ids.getFirst();}
    private PayoutView replay(UUID actor,String op,String key,byte[] hash){try{Map<String,Object> r=jdbc.queryForMap("SELECT request_hash,repasse_id FROM repasse_idempotencia WHERE ator_id=? AND operacao=? AND chave=?",actor,op,key);if(!MessageDigest.isEqual((byte[])r.get("request_hash"),hash))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","A chave já foi usada com dados diferentes.");return view((UUID)r.get("repasse_id"));}catch(EmptyResultDataAccessException ex){return null;}}
    private Map<String,Object> lock(UUID id){try{return jdbc.queryForMap("SELECT * FROM repasse WHERE id=? FOR UPDATE",id);}catch(EmptyResultDataAccessException ex){throw notFound();}}
    private Map<String,Object> find(UUID id){try{return jdbc.queryForMap("SELECT * FROM repasse WHERE id=?",id);}catch(EmptyResultDataAccessException ex){throw notFound();}}
    private PayoutView view(UUID id){return jdbc.queryForObject("SELECT id,pedido_id,valor,moeda,estado,divergencia_codigo,solicitado_em,confirmado_em,version FROM repasse WHERE id=?",(rs,n)->new PayoutView(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getBigDecimal(3),rs.getString(4),rs.getString(5),provider.configured()?"DISPONIVEL":"INDISPONIVEL",rs.getString(6),rs.getTimestamp(7)==null?null:rs.getTimestamp(7).toInstant(),rs.getTimestamp(8)==null?null:rs.getTimestamp(8).toInstant(),rs.getLong(9)),id);}
    private byte[] hash(String value){try{return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));}catch(Exception ex){throw new IllegalStateException(ex);}}
    private void requireKey(String key){if(key==null||key.isBlank())throw error(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_REQUIRED","A operação exige Idempotency-Key.");}
    private static OrderService.OrderException notFound(){return error(HttpStatus.NOT_FOUND,"NOT_FOUND","Repasse não encontrado.");}
    private static OrderService.OrderException error(HttpStatus s,String c,String m){return OrderService.error(s,c,m);}
}
