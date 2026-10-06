package br.com.exameperto.identity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
class OrderService {
    private final JdbcTemplate jdbc; private final DataProtector protector; private final ObjectMapper json; private final RouteProvider routes; private final DelivererService evidence; private final PaymentProvider payments;
    OrderService(JdbcTemplate jdbc, DataProtector protector, ObjectMapper json, RouteProvider routes, DelivererService evidence, PaymentProvider payments){this.jdbc=jdbc;this.protector=protector;this.json=json;this.routes=routes;this.evidence=evidence;this.payments=payments;}

    @Transactional
    OrderView create(UUID actor, OrderInput input, String idem) {
        requireAddress(input.origin()); requireAddress(input.destination()); authorizePedidos(actor,input.patientId()); authorizeRecipient(input.patientId(),input.recipientUserId());
        requireOwnedAuthorization(actor,input.pickupAuthorizationDocumentId());
        UUID existing=replay(actor,"CREATE_ORDER",idem,hash(input)); if(existing!=null)return order(existing);
        UUID id=UUID.randomUUID(); UUID auth=UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO pedido(id,paciente_id,solicitante_id,origem_cifrada,destino_cifrada,origem_lat,origem_lon,destino_lat,destino_lon,destinatario_id,unidade_id,estado) VALUES (?,?,?,?,?,?,?,?,?,?,?,'EM_VERIFICACAO')",
                id,input.patientId(),actor,protect(input.origin()),protect(input.destination()),input.origin().latitude(),input.origin().longitude(),input.destination().latitude(),input.destination().longitude(),input.recipientUserId(),input.pickupUnitId());
            jdbc.update("INSERT INTO autorizacao_retirada(id,pedido_id,documento_id,estado,criada_por,valida_ate) VALUES (?,?,?,'PENDENTE',?,clock_timestamp()+interval '24 hours')",auth,id,input.pickupAuthorizationDocumentId(),actor);
            if(idem!=null)jdbc.update("INSERT INTO pedido_idempotencia(ator_id,operacao,chave,request_hash,pedido_id) VALUES (?,?,?,?,?)",actor,"CREATE_ORDER",idem,hash(input),id);
        } catch (RuntimeException ex) { throw error(HttpStatus.CONFLICT,"ORDER_CONFLICT","Não foi possível registrar o pedido."); }
        return order(id);
    }
    DocumentView uploadAuthorizationDocument(UUID actor, org.springframework.web.multipart.MultipartFile file){return evidence.storeDocument(actor,"AUTORIZACAO_RETIRADA",file);}

    List<OrderView> list(UUID actor){return jdbc.query("SELECT p.id FROM pedido p LEFT JOIN autorizacao_paciente a ON a.paciente_id=p.paciente_id AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() JOIN paciente patient ON patient.id=p.paciente_id WHERE patient.usuario_id=? OR (a.id IS NOT NULL AND EXISTS (SELECT 1 FROM autorizacao_escopo e WHERE e.autorizacao_id=a.id AND e.escopo='PEDIDOS')) GROUP BY p.id,p.created_at ORDER BY p.created_at DESC,p.id",(rs,n)->order(rs.getObject(1,UUID.class)),actor,actor);}
    OrderView get(UUID actor,UUID id){authorizeRead(actor,id);return order(id);}
    OrderAddressesView addresses(UUID actor,UUID id){authorizeRead(actor,id);Map<String,Object> r=row(id);return new OrderAddressesView(read((byte[])r.get("origem_cifrada")),read((byte[])r.get("destino_cifrada")));}

    @Transactional
    OrderView updateAddresses(UUID actor,UUID id,OrderAddressUpdateInput input){
        requireAddress(input.origin()); requireAddress(input.destination()); authorizeRead(actor,id); Map<String,Object> row=forUpdate(id); requireVersion(row,input.version());
        releaseCoverageForReplacement(id);
        jdbc.update("UPDATE pedido SET origem_cifrada=?,destino_cifrada=?,origem_lat=?,origem_lon=?,destino_lat=?,destino_lon=?,estado='EM_VERIFICACAO',version=version+1,updated_at=clock_timestamp() WHERE id=? AND version=?",protect(input.origin()),protect(input.destination()),input.origin().latitude(),input.origin().longitude(),input.destination().latitude(),input.destination().longitude(),id,input.version());
        jdbc.update("UPDATE orcamento SET estado='SUBSTITUIDO',version=version+1 WHERE pedido_id=? AND estado IN ('PROPOSTO','ACEITO')",id); return order(id);
    }

    @Transactional
    OrderView attachAuthorization(UUID actor,UUID orderId,org.springframework.web.multipart.MultipartFile file){
        authorizeRead(actor,orderId); Map<String,Object> o=forUpdate(orderId); DelivererService.StoredEvidence stored=evidence.storeEvidence(actor,"AUTORIZACAO_RETIRADA",file);
        try {Map<String,Object> current=authorization(orderId); jdbc.update("UPDATE autorizacao_retirada SET documento_id=?,estado='PENDENTE',revogada_em=NULL WHERE id=?",stored.id(),current.get("id")); return order(orderId);} catch(RuntimeException ex){evidence.discardEvidence(stored);throw ex;}
    }

    @Transactional
    QuoteView quote(UUID actor,UUID orderId,long version,String idem,QuoteInput input){
        authorizeRead(actor,orderId); Map<String,Object> o=forUpdate(orderId);
        UUID old=replayQuote(actor,idem,hash(input)); if(old!=null)return quote(old);
        requireVersion(o,version); expireQuotes(orderId);
        Map<String,Object> auth=authorization(orderId); if(!"VERIFICADA".equals(auth.get("estado"))||auth.get("revogada_em")!=null||((java.sql.Timestamp)auth.get("valida_ate")).toInstant().isBefore(Instant.now()))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"UNIT_NOT_CONFIRMED","A unidade ainda não confirmou o procedimento de retirada.");
        Address origin=read((byte[])o.get("origem_cifrada")), destination=read((byte[])o.get("destino_cifrada"));
        RouteProvider.RouteResult route=routes.route(origin,destination); validateRoute(route);
        Map<String,Object> tariff=activeTariff(); BigDecimal amount=calculate(route,tariff); UUID id=UUID.randomUUID(); Instant now=Instant.now();
        CoverageSplit split=coverage(input.programId(),(UUID)o.get("paciente_id"),amount);
        UUID cancellationPolicy=activeCancellationPolicy();
        String snapshot=String.valueOf(tariff.get("parametros"));
        jdbc.update("UPDATE orcamento SET estado='SUBSTITUIDO',version=version+1 WHERE pedido_id=? AND estado='PROPOSTO'",orderId);
        jdbc.update("INSERT INTO orcamento(id,pedido_id,paciente_id,tarifa_id,rota_provedor,rota_referencia,distancia_m,duracao_s,rota_calculada_em,transito_incluido,politica_snapshot,frete,paciente_valor,subsidio_valor,moeda,expira_em,estado,programa_id,beneficio_solicitacao_id,politica_cancelamento_id,pedido_version) VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,'BRL',?,'PROPOSTO',?,?,?,?)",
            id,orderId,o.get("paciente_id"),tariff.get("id"),route.provider(),route.reference(),route.distanceMeters(),route.durationSeconds(),java.sql.Timestamp.from(route.calculatedAt()),route.trafficIncluded(),snapshot,amount,split.patient(),split.subsidy(),java.sql.Timestamp.from(now.plusSeconds(validitySeconds(tariff))),input.programId(),split.benefitRequest(),cancellationPolicy,version+1);
        jdbc.update("UPDATE pedido SET estado='AGUARDANDO_ACEITE',version=version+1,updated_at=clock_timestamp() WHERE id=? AND version=?",orderId,version);
        if(idem!=null)jdbc.update("INSERT INTO pedido_idempotencia(ator_id,operacao,chave,request_hash,orcamento_id) VALUES (?,?,?,?,?)",actor,"CREATE_QUOTE",idem,hash(input),id);
        return quote(id);
    }

    List<QuoteView> quotes(UUID actor,UUID orderId){authorizeRead(actor,orderId);expireQuotes(orderId);return jdbc.query("SELECT id FROM orcamento WHERE pedido_id=? ORDER BY created_at DESC,id",(rs,n)->quote(rs.getObject(1,UUID.class)),orderId);}
    QuoteView getQuote(UUID actor,UUID id){Map<String,Object> q=quoteRow(id);authorizeRead(actor,(UUID)q.get("pedido_id"));expireQuotes((UUID)q.get("pedido_id"));return quote(id);}

    @Transactional
    OrderView accept(UUID actor,UUID quoteId,long quoteVersion,String idem,AcceptQuoteInput input){
        if(idem==null||idem.isBlank())throw error(HttpStatus.BAD_REQUEST,"IDEMPOTENCY_REQUIRED","O aceite financeiro exige Idempotency-Key.");
        byte[] requestHash=hash(input); OrderView prior=replayAcceptance(actor,quoteId,idem,requestHash); if(prior!=null)return prior;
        Map<String,Object> initial=quoteRow(quoteId); UUID orderId=(UUID)initial.get("pedido_id");
        authorizeRead(actor,orderId); Map<String,Object> order=forUpdate(orderId); Map<String,Object> q=quoteForUpdate(quoteId);
        authorizeRead(actor,orderId);
        prior=replayAcceptance(actor,quoteId,idem,requestHash); if(prior!=null)return prior;
        if(((Number)q.get("version")).longValue()!=quoteVersion)throw error(HttpStatus.PRECONDITION_FAILED,"VERSION_MISMATCH","O orçamento mudou; recarregue antes de aceitar.");
        if(((Number)order.get("version")).longValue()!=input.acceptedOrderVersion()||!java.util.Objects.equals(q.get("pedido_version"),input.acceptedOrderVersion()))throw error(HttpStatus.PRECONDITION_FAILED,"VERSION_MISMATCH","O pedido mudou; solicite novo orçamento.");
        if(!"PROPOSTO".equals(q.get("estado")))throw error(HttpStatus.CONFLICT,"QUOTE_NOT_CURRENT","O orçamento não está disponível para aceite.");
        if(((java.sql.Timestamp)q.get("expira_em")).toInstant().compareTo(Instant.now())<=0){jdbc.update("UPDATE orcamento SET estado='EXPIRADO',version=version+1 WHERE id=?",quoteId);throw error(HttpStatus.CONFLICT,"QUOTE_EXPIRED","O orçamento venceu; solicite outro.");}
        requireAcceptedTerms(q,input);
        BigDecimal patient=(BigDecimal)q.get("paciente_valor"), subsidy=(BigDecimal)q.get("subsidio_valor");
        if(patient.signum()>0&&!payments.configured())throw error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","O provedor de pagamento não está habilitado.");
        if(subsidy.signum()>0)reserveSubsidy(q,subsidy);
        UUID acceptance=UUID.randomUUID();
        try {
            String snapshot=json.writeValueAsString(Map.ofEntries(Map.entry("quoteId",quoteId),Map.entry("orderVersion",input.acceptedOrderVersion()),Map.entry("quoteVersion",quoteVersion),Map.entry("grossAmount",q.get("frete").toString()),Map.entry("patientAmount",patient.toString()),Map.entry("subsidyAmount",subsidy.toString()),Map.entry("currency","BRL"),Map.entry("cancellationPolicyId",input.acceptedCancellationPolicyId()),Map.entry("routeProvider",q.get("rota_provedor")),Map.entry("routeReference",q.get("rota_referencia")),Map.entry("tariffSnapshot",q.get("politica_snapshot"))));
            jdbc.update("INSERT INTO aceite_orcamento(id,orcamento_id,pedido_id,paciente_id,aceito_por,pedido_version,orcamento_version,condicoes_snapshot,frete,paciente_valor,subsidio_valor,moeda) VALUES (?,?,?,?,?,?,?,?::jsonb,?,?,?,'BRL')",acceptance,quoteId,orderId,q.get("paciente_id"),actor,input.acceptedOrderVersion(),quoteVersion,snapshot,q.get("frete"),patient,subsidy);
        } catch(JsonProcessingException ex){throw new IllegalStateException(ex);}
        jdbc.update("UPDATE orcamento SET estado='ACEITO',aceito_em=clock_timestamp(),version=version+1 WHERE id=?",quoteId);
        if(patient.signum()>0){UUID op=UUID.randomUUID();String key="cobranca:"+quoteId;jdbc.update("INSERT INTO operacao_financeira(id,pedido_id,tipo,chave_negocio,valor,moeda,estado,provedor,beneficiario_referencia) VALUES (?,?, 'COBRANCA',?,?,'BRL','PENDENTE',?,?)",op,orderId,key,patient,payments.providerId(),q.get("paciente_id").toString());jdbc.update("INSERT INTO financeiro_outbox(id,operacao_id,pedido_id,tipo,chave,payload_saneado,estado) VALUES (?,?,?,'CRIAR_COBRANCA',?,jsonb_build_object('operationId',?::text,'orderId',?::text,'amount',?::text,'currency','BRL'),'PENDENTE')",UUID.randomUUID(),op,orderId,key,op,orderId,patient);}
        jdbc.update("UPDATE pedido SET estado=?,version=version+1,updated_at=clock_timestamp() WHERE id=?",patient.signum()>0?"AGUARDANDO_ACEITE":"DISPONIVEL",orderId);
        jdbc.update("INSERT INTO aceite_idempotencia(ator_id,operacao,chave,request_hash,pedido_id,aceite_id) VALUES (?,?,?,?,?,?)",actor,"ACCEPT:"+quoteId,idem,requestHash,orderId,acceptance);
        return order(orderId);
    }

    @Transactional
    boolean paymentEvent(byte[] body,Map<String,String> headers){
        if(!payments.configured())throw error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","O provedor de pagamento não está habilitado.");
        PaymentProvider.PaymentEvent event=payments.authenticateAndParse(body,headers);
        if(event==null||event.externalEventId()==null||event.operationId()==null||event.result()==null||event.amount()==null||event.occurredAt()==null)throw error(HttpStatus.BAD_REQUEST,"INVALID_EVENT","O evento autenticado está incompleto.");
        byte[] payloadHash=hashBytes(body); String provider=payments.providerId();
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",(rs,n)->0,provider+":"+event.externalEventId());
        try{Map<String,Object> old=jdbc.queryForMap("SELECT payload_hash FROM evento_pagamento WHERE provedor=? AND evento_externo_id=?",provider,event.externalEventId());if(!MessageDigest.isEqual((byte[])old.get("payload_hash"),payloadHash))throw error(HttpStatus.CONFLICT,"EVENT_CONFLICT","O identificador do evento foi reutilizado com conteúdo divergente.");return false;}catch(EmptyResultDataAccessException ignored){}
        Map<String,Object> op;try{op=jdbc.queryForMap("SELECT * FROM operacao_financeira WHERE id=? FOR UPDATE",event.operationId());}catch(EmptyResultDataAccessException ex){throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Operação financeira não encontrada.");}
        UUID orderId=(UUID)op.get("pedido_id");Map<String,Object> order=forUpdate(orderId);
        boolean valid="COBRANCA".equals(op.get("tipo"))&&"BRL".equals(event.currency())&&((BigDecimal)op.get("valor")).compareTo(event.amount())==0&&java.util.Objects.equals(op.get("beneficiario_referencia"),event.beneficiaryReference());
        if(!valid){jdbc.update("INSERT INTO evento_pagamento(provedor,evento_externo_id,payload_hash,operacao_id,estado,autenticado_em) VALUES (?,?,?,?,'REJEITADO',clock_timestamp())",provider,event.externalEventId(),payloadHash,event.operationId());throw error(HttpStatus.CONFLICT,"PAYMENT_DIVERGENCE","Valor, moeda ou destinatário diverge da operação.");}
        String state=switch(event.result()){case CONFIRMED->"CONFIRMADA";case FAILED->"FALHOU";case UNCERTAIN->"INCERTA";};
        boolean current=jdbc.queryForObject("SELECT count(*) FROM orcamento q JOIN aceite_orcamento a ON a.orcamento_id=q.id WHERE a.pedido_id=? AND q.estado='ACEITO'",Long.class,orderId)==1;
        if("CONFIRMADA".equals(op.get("estado"))&&event.result()!=PaymentProvider.Result.CONFIRMED){jdbc.update("INSERT INTO evento_pagamento(provedor,evento_externo_id,payload_hash,operacao_id,estado,autenticado_em) VALUES (?,?,?,?,'RECONCILIAR',clock_timestamp())",provider,event.externalEventId(),payloadHash,event.operationId());return true;}
        if(event.result()==PaymentProvider.Result.CONFIRMED&&current&&!List.of("CANCELADA","EM_VERIFICACAO").contains(order.get("estado")))jdbc.update("UPDATE pedido SET estado='DISPONIVEL',version=version+1,updated_at=clock_timestamp() WHERE id=?",orderId);
        else if(event.result()==PaymentProvider.Result.CONFIRMED&&!current)state="RECONCILIAR";
        jdbc.update("UPDATE operacao_financeira SET estado=?,updated_at=clock_timestamp() WHERE id=?",state,event.operationId());
        jdbc.update("INSERT INTO evento_pagamento(provedor,evento_externo_id,payload_hash,operacao_id,estado,autenticado_em) VALUES (?,?,?,?,?,clock_timestamp())",provider,event.externalEventId(),payloadHash,event.operationId(),"RECONCILIAR".equals(state)?"RECONCILIAR":"PROCESSADO");return true;
    }

    private BigDecimal calculate(RouteProvider.RouteResult route,Map<String,Object> tariff){
        if(!"BASE_KM_MINUTO".equals(tariff.get("formula_codigo")))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","A fórmula tarifária não está definida.");
        try {
            Map<?,?> p=json.readValue(String.valueOf(tariff.get("parametros")),Map.class); BigDecimal base=decimal(p,"base"),km=decimal(p,"perKm"),minute=decimal(p,"perMinute"),minimum=decimal(p,"minimum");
            BigDecimal value=base.add(km.multiply(BigDecimal.valueOf(route.distanceMeters()).divide(BigDecimal.valueOf(1000),6,RoundingMode.HALF_UP))).add(minute.multiply(BigDecimal.valueOf(route.durationSeconds()).divide(BigDecimal.valueOf(60),6,RoundingMode.HALF_UP)));
            return value.max(minimum).setScale(2,RoundingMode.HALF_UP);
        } catch(Exception ex){throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","Parâmetros tarifários inválidos.");}
    }
    private BigDecimal decimal(Map<?,?> p,String key){Object v=p.get(key);if(v==null)throw new IllegalArgumentException();return new BigDecimal(String.valueOf(v));}
    private long validitySeconds(Map<String,Object> tariff){try{Map<?,?> p=json.readValue(String.valueOf(tariff.get("parametros")),Map.class);Object value=p.containsKey("validitySeconds")?p.get("validitySeconds"):900;return Long.parseLong(String.valueOf(value));}catch(Exception e){return 900;}}
    private Map<String,Object> activeTariff(){try{return jdbc.queryForMap("SELECT * FROM tarifa WHERE estado='ATIVA' AND inicio<=clock_timestamp() AND (fim IS NULL OR fim>clock_timestamp()) ORDER BY numero DESC LIMIT 1");}catch(EmptyResultDataAccessException e){throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","Não há política tarifária vigente.");}}
    private UUID activeCancellationPolicy(){try{return jdbc.queryForObject("SELECT id FROM politica_cancelamento WHERE estado='ATIVA' AND inicio<=clock_timestamp() AND (fim IS NULL OR fim>clock_timestamp()) ORDER BY numero DESC LIMIT 1",UUID.class);}catch(EmptyResultDataAccessException e){return null;}}
    private CoverageSplit coverage(UUID program,UUID patient,BigDecimal amount){
        if(program==null)return new CoverageSplit(amount,BigDecimal.ZERO,null);
        Map<String,Object> p;try{p=jdbc.queryForMap("SELECT id,instituicao_id FROM programa WHERE id=? AND estado='HABILITADO'",program);}catch(EmptyResultDataAccessException e){throw error(HttpStatus.UNPROCESSABLE_ENTITY,"PROGRAM_UNAVAILABLE","O programa não está habilitado.");}
        Map<String,Object> b;try{b=jdbc.queryForMap("SELECT s.id,s.percentual FROM solicitacao_beneficio s JOIN politica_beneficio pb ON pb.id=s.politica_id WHERE s.paciente_id=? AND s.instituicao_id=? AND s.estado='DECIDIDA' AND s.decisao='APROVADA' AND s.percentual>0 AND s.decisao_revogada_em IS NULL AND s.decisao_valida_ate>clock_timestamp() AND pb.estado='ATIVA' AND pb.vigencia_inicio<=clock_timestamp() AND (pb.vigencia_fim IS NULL OR pb.vigencia_fim>clock_timestamp()) ORDER BY s.updated_at DESC LIMIT 1",patient,p.get("instituicao_id"));}catch(EmptyResultDataAccessException e){throw error(HttpStatus.UNPROCESSABLE_ENTITY,"BENEFIT_UNAVAILABLE","Não há benefício válido para este paciente e programa.");}
        BigDecimal subsidy=amount.multiply((BigDecimal)b.get("percentual")).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP).min(amount);
        return new CoverageSplit(amount.subtract(subsidy),subsidy,(UUID)b.get("id"));
    }
    private void requireAcceptedTerms(Map<String,Object> q,AcceptQuoteInput input){
        if(((BigDecimal)q.get("frete")).compareTo(input.acceptedGrossAmount())!=0||((BigDecimal)q.get("paciente_valor")).compareTo(input.acceptedPatientAmount())!=0||input.acceptedCurrency()!=AcceptQuoteInput.Currency.BRL)throw error(HttpStatus.CONFLICT,"ACCEPTED_TERMS_DIVERGE","Os valores aceitos divergem do orçamento atual.");
        if(q.get("politica_cancelamento_id")==null)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","A política de cancelamento ainda não foi definida.");
        if(!q.get("politica_cancelamento_id").equals(input.acceptedCancellationPolicyId()))throw error(HttpStatus.CONFLICT,"ACCEPTED_TERMS_DIVERGE","A política aceita diverge da proposta.");
    }
    private void reserveSubsidy(Map<String,Object> q,BigDecimal value){
        UUID program=(UUID)q.get("programa_id"), benefit=(UUID)q.get("beneficio_solicitacao_id");
        if(program==null||benefit==null)throw error(HttpStatus.CONFLICT,"FUNDING_CONFLICT","O subsídio não possui origem válida.");
        long valid=jdbc.queryForObject("SELECT count(*) FROM solicitacao_beneficio s JOIN programa p ON p.instituicao_id=s.instituicao_id JOIN politica_beneficio pb ON pb.id=s.politica_id WHERE s.id=? AND p.id=? AND p.estado='HABILITADO' AND s.estado='DECIDIDA' AND s.decisao='APROVADA' AND s.decisao_revogada_em IS NULL AND s.decisao_valida_ate>clock_timestamp() AND pb.estado='ATIVA' AND pb.vigencia_inicio<=clock_timestamp() AND (pb.vigencia_fim IS NULL OR pb.vigencia_fim>clock_timestamp())",Long.class,benefit,program);
        if(valid!=1)throw error(HttpStatus.CONFLICT,"BENEFIT_UNAVAILABLE","O benefício ou programa deixou de estar válido.");
        jdbc.update("INSERT INTO conta_programa(programa_id,disponivel) VALUES (?,COALESCE((SELECT SUM(valor) FROM lancamento_aporte WHERE programa_id=? AND moeda='BRL'),0)) ON CONFLICT (programa_id) DO NOTHING",program,program);
        int changed=jdbc.update("UPDATE conta_programa SET disponivel=disponivel-?,reservado=reservado+?,version=version+1 WHERE programa_id=? AND disponivel>=?",value,value,program,value);
        if(changed!=1)throw error(HttpStatus.CONFLICT,"FUNDING_INSUFFICIENT","O programa não possui cobertura disponível para este orçamento.");
        UUID reservation=UUID.randomUUID();jdbc.update("INSERT INTO reserva_subsidio(id,orcamento_id,pedido_id,programa_id,paciente_id,valor,moeda,estado) VALUES (?,?,?,?,?,?,'BRL','RESERVADA')",reservation,q.get("id"),q.get("pedido_id"),program,q.get("paciente_id"),value);
        jdbc.update("INSERT INTO operacao_financeira(id,pedido_id,programa_id,tipo,chave_negocio,valor,moeda,estado) VALUES (?,?,?,'RESERVA',?,?,'BRL','CONFIRMADA')",UUID.randomUUID(),q.get("pedido_id"),program,"reserva:"+q.get("id"),value);
    }
    private void releaseCoverageForReplacement(UUID orderId){
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.* FROM reserva_subsidio r JOIN orcamento q ON q.id=r.orcamento_id WHERE r.pedido_id=? AND r.estado='RESERVADA' AND q.estado='ACEITO'",orderId);
        for(Map<String,Object> r:rows){BigDecimal value=((BigDecimal)r.get("valor")).subtract((BigDecimal)r.get("valor_liquidado")).subtract((BigDecimal)r.get("valor_liberado"));jdbc.update("UPDATE conta_programa SET disponivel=disponivel+?,reservado=reservado-?,version=version+1 WHERE programa_id=?",value,value,r.get("programa_id"));jdbc.update("UPDATE reserva_subsidio SET valor_liberado=valor_liberado+?,estado='LIBERADA' WHERE id=?",value,r.get("id"));jdbc.update("INSERT INTO operacao_financeira(id,pedido_id,programa_id,tipo,chave_negocio,valor,moeda,estado) VALUES (?,?,?,'LIBERACAO',?,?,'BRL','CONFIRMADA')",UUID.randomUUID(),orderId,r.get("programa_id"),"liberacao:endereco:"+r.get("orcamento_id"),value);}
        jdbc.update("UPDATE operacao_financeira SET estado='RECONCILIAR',updated_at=clock_timestamp() WHERE pedido_id=? AND tipo='COBRANCA' AND estado IN ('PENDENTE','PROCESSANDO','CONFIRMADA','INCERTA')",orderId);
        jdbc.update("UPDATE financeiro_outbox SET estado='RECONCILIAR' WHERE pedido_id=? AND tipo='CRIAR_COBRANCA' AND estado IN ('PENDENTE','EM_ENVIO','ENVIADO')",orderId);
    }
    private void validateRoute(RouteProvider.RouteResult r){if(r==null||r.provider()==null||r.provider().isBlank()||r.reference()==null||r.reference().isBlank()||r.distanceMeters()<=0||r.durationSeconds()<=0||r.calculatedAt()==null)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_ROUTE","A resposta de rota não contém distância/duração válidas.");}
    private void expireQuotes(UUID order){jdbc.update("UPDATE orcamento SET estado='EXPIRADO',version=version+1 WHERE pedido_id=? AND estado='PROPOSTO' AND expira_em<=clock_timestamp()",order);}
    private void authorizePedidos(UUID actor,UUID patient){if(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE id=? AND usuario_id=?",Long.class,patient,actor)==1)return;long n=jdbc.queryForObject("SELECT count(*) FROM autorizacao_paciente a JOIN autorizacao_escopo e ON e.autorizacao_id=a.id WHERE a.paciente_id=? AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() AND e.escopo='PEDIDOS'",Long.class,patient,actor);if(n!=1)throw notFound();}
    private void authorizeRecipient(UUID patient,UUID recipient){UUID owner=jdbc.queryForObject("SELECT usuario_id FROM paciente WHERE id=?",UUID.class,patient);if(owner.equals(recipient))return;long n=jdbc.queryForObject("SELECT count(*) FROM autorizacao_paciente a JOIN autorizacao_escopo e ON e.autorizacao_id=a.id WHERE a.paciente_id=? AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() AND e.escopo='RECEBIMENTO'",Long.class,patient,recipient);if(n!=1)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"RECIPIENT_UNAUTHORIZED","Destinatário não possui RECEBIMENTO vigente.");}
    private void requireOwnedAuthorization(UUID actor,UUID doc){try{Map<String,Object> d=jdbc.queryForMap("SELECT proprietario_id,categoria,estado FROM documento WHERE id=?",doc);if(!actor.equals(d.get("proprietario_id"))||!"AUTORIZACAO_RETIRADA".equals(d.get("categoria"))||List.of("REJEITADO","EXPURGADO").contains(d.get("estado")))throw notFound();}catch(EmptyResultDataAccessException e){throw notFound();}}
    private void authorizeRead(UUID actor,UUID id){Map<String,Object> r=row(id);UUID patientUser=jdbc.queryForObject("SELECT usuario_id FROM paciente WHERE id=?",UUID.class,r.get("paciente_id"));if(patientUser.equals(actor))return;long n=jdbc.queryForObject("SELECT count(*) FROM autorizacao_paciente a JOIN autorizacao_escopo e ON e.autorizacao_id=a.id WHERE a.paciente_id=? AND a.familiar_id=? AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() AND e.escopo='PEDIDOS'",Long.class,r.get("paciente_id"),actor);if(n!=1)throw notFound();}
    private Map<String,Object> authorization(UUID id){return jdbc.queryForMap("SELECT * FROM autorizacao_retirada WHERE pedido_id=?",id);}
    private Map<String,Object> row(UUID id){try{return jdbc.queryForMap("SELECT * FROM pedido WHERE id=?",id);}catch(EmptyResultDataAccessException e){throw notFound();}}
    private Map<String,Object> forUpdate(UUID id){try{return jdbc.queryForMap("SELECT * FROM pedido WHERE id=? FOR UPDATE",id);}catch(EmptyResultDataAccessException e){throw notFound();}}
    private Map<String,Object> quoteRow(UUID id){try{return jdbc.queryForMap("SELECT * FROM orcamento WHERE id=?",id);}catch(EmptyResultDataAccessException e){throw notFound();}}
    private Map<String,Object> quoteForUpdate(UUID id){try{return jdbc.queryForMap("SELECT * FROM orcamento WHERE id=? FOR UPDATE",id);}catch(EmptyResultDataAccessException e){throw notFound();}}
    private OrderView order(UUID id){Map<String,Object> r=row(id);Map<String,Object> a=authorization(id);return new OrderView(id,(UUID)r.get("paciente_id"),(String)r.get("estado"),coverageStatus(id),((java.sql.Timestamp)r.get("created_at")).toInstant(),((Number)r.get("version")).longValue(),(UUID)a.get("id"),(UUID)r.get("unidade_id"));}
    private String coverageStatus(UUID id){try{Map<String,Object> q=jdbc.queryForMap("SELECT paciente_valor,subsidio_valor FROM orcamento WHERE pedido_id=? AND estado='ACEITO'",id);BigDecimal patient=(BigDecimal)q.get("paciente_valor");if(patient.signum()==0)return "CONFIRMADA";String state=jdbc.queryForObject("SELECT estado FROM operacao_financeira WHERE pedido_id=? AND tipo='COBRANCA' ORDER BY created_at DESC LIMIT 1",String.class,id);return switch(state){case "CONFIRMADA"->"CONFIRMADA";case "FALHOU"->"PAGAMENTO_FALHOU";case "INCERTA","RECONCILIAR"->"RECONCILIAR";default->"PENDENTE_PAGAMENTO";};}catch(EmptyResultDataAccessException e){return "NAO_CONTRATADA";}}
    private QuoteView quote(UUID id){Map<String,Object> r=quoteRow(id);int tariffVersion=jdbc.queryForObject("SELECT numero FROM tarifa WHERE id=?",Integer.class,r.get("tarifa_id"));return new QuoteView(id,(UUID)r.get("pedido_id"),(String)r.get("estado"),(BigDecimal)r.get("frete"),(BigDecimal)r.get("paciente_valor"),(BigDecimal)r.get("subsidio_valor"),(UUID)r.get("programa_id"),(UUID)r.get("politica_cancelamento_id"),tariffVersion,(String)r.get("rota_provedor"),(String)r.get("rota_referencia"),((Number)r.get("distancia_m")).intValue(),((Number)r.get("duracao_s")).intValue(),(Boolean)r.get("transito_incluido"),((java.sql.Timestamp)r.get("rota_calculada_em")).toInstant(),((java.sql.Timestamp)r.get("expira_em")).toInstant(),((Number)r.get("version")).longValue());}
    private Address read(byte[] value){try{return json.readValue(protector.decrypt(value),Address.class);}catch(Exception e){throw error(HttpStatus.INTERNAL_SERVER_ERROR,"DATA_UNAVAILABLE","Endereço protegido indisponível.");}}
    private byte[] protect(Address a){try{return protector.encrypt(json.writeValueAsString(a));}catch(JsonProcessingException e){throw error(HttpStatus.INTERNAL_SERVER_ERROR,"DATA_UNAVAILABLE","Endereço protegido indisponível.");}}
    private byte[] hash(Object input){try{return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(input));}catch(Exception e){throw new IllegalStateException(e);}}
    private UUID replay(UUID actor,String op,String key,byte[] hash){if(key==null)return null;try{Map<String,Object> r=jdbc.queryForMap("SELECT request_hash,pedido_id FROM pedido_idempotencia WHERE ator_id=? AND operacao=? AND chave=?",actor,op,key);if(!MessageDigest.isEqual((byte[])r.get("request_hash"),hash))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","A chave já foi usada com dados diferentes.");return (UUID)r.get("pedido_id");}catch(EmptyResultDataAccessException e){return null;}}
    private UUID replayQuote(UUID actor,String key,byte[] hash){if(key==null)return null;try{Map<String,Object> r=jdbc.queryForMap("SELECT request_hash,orcamento_id FROM pedido_idempotencia WHERE ator_id=? AND operacao='CREATE_QUOTE' AND chave=?",actor,key);if(!MessageDigest.isEqual((byte[])r.get("request_hash"),hash))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","A chave já foi usada com dados diferentes.");return (UUID)r.get("orcamento_id");}catch(EmptyResultDataAccessException e){return null;}}
    private OrderView replayAcceptance(UUID actor,UUID quote,String key,byte[] requestHash){try{Map<String,Object> r=jdbc.queryForMap("SELECT request_hash,pedido_id FROM aceite_idempotencia WHERE ator_id=? AND operacao=? AND chave=?",actor,"ACCEPT:"+quote,key);if(!MessageDigest.isEqual((byte[])r.get("request_hash"),requestHash))throw error(HttpStatus.CONFLICT,"IDEMPOTENCY_CONFLICT","A chave já foi usada com dados diferentes.");authorizeRead(actor,(UUID)r.get("pedido_id"));return order((UUID)r.get("pedido_id"));}catch(EmptyResultDataAccessException e){return null;}}
    private byte[] hashBytes(byte[] value){try{return MessageDigest.getInstance("SHA-256").digest(value);}catch(Exception e){throw new IllegalStateException(e);}}
    private void requireVersion(Map<String,Object> r,long v){if(((Number)r.get("version")).longValue()!=v)throw error(HttpStatus.PRECONDITION_FAILED,"VERSION_MISMATCH","O pedido mudou; recarregue antes de cotar.");}
    private void requireAddress(Address a){if(a.latitude()==null||a.longitude()==null||!a.postalCode().matches("\\d{8}"))throw error(HttpStatus.BAD_REQUEST,"INVALID_ADDRESS","Endereço ou coordenadas inválidos.");}
    private static OrderException notFound(){return error(HttpStatus.NOT_FOUND,"NOT_FOUND","Pedido não encontrado.");}
    static OrderException error(HttpStatus s,String c,String m){return new OrderException(s,c,m);}
    static final class OrderException extends org.springframework.web.server.ResponseStatusException {private final ApiError body;OrderException(HttpStatus s,String c,String m){super(s,m);body=new ApiError(c,m,UUID.randomUUID(),s.is5xxServerError());}ApiError body(){return body;}}
    private record CoverageSplit(BigDecimal patient,BigDecimal subsidy,UUID benefitRequest){}
}
