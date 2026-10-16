package br.com.exameperto.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class PaymentService {
    private final JdbcTemplate jdbc; private final PaymentProvider provider; private final OrderService orders; private final ObjectMapper json; private final String payerEmail;
    PaymentService(JdbcTemplate jdbc,PaymentProvider provider,@Lazy OrderService orders,ObjectMapper json,@Value("${payment.mercadopago.sandbox-payer-email:}") String payerEmail){this.jdbc=jdbc;this.provider=provider;this.orders=orders;this.json=json;this.payerEmail=payerEmail;}

    @Transactional(noRollbackFor=OrderService.OrderException.class)
    CheckoutView checkout(AuthService.SessionPrincipal principal,UUID id){
        Map<String,Object> current=find(id);authorize(current,principal.userId());if(!"COBRANCA".equals(current.get("tipo")))throw error(HttpStatus.CONFLICT,"PAYMENT_NOT_CHARGE","A operação não é uma cobrança particular.");
        if("CONFIRMADA".equals(current.get("estado")))throw error(HttpStatus.CONFLICT,"PAYMENT_ALREADY_CONFIRMED","Esta cobrança já está confirmada.");
        if(current.get("checkout_url")!=null&&current.get("checkout_expires_at")!=null&&((java.sql.Timestamp)current.get("checkout_expires_at")).toInstant().isAfter(Instant.now()))return view(current);
        if(!provider.configured())throw error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","O pagamento de teste não está habilitado.");
        Map<String,Object> row=lock(id);int attempt=((Number)row.get("checkout_attempt")).intValue()+1;UUID operation=(UUID)row.get("id");String beneficiary=String.valueOf(row.get("beneficiario_referencia"));String external="exameperto:"+operation+":"+beneficiary;PaymentProvider.CheckoutRequest request=new PaymentProvider.CheckoutRequest(operation,(BigDecimal)row.get("valor"),String.valueOf(row.get("moeda")),external,beneficiary,payerEmail);
        jdbc.update("UPDATE operacao_financeira SET estado='PROCESSANDO',provedor=?,checkout_attempt=?,updated_at=clock_timestamp() WHERE id=?",provider.providerId(),attempt,id);
        try{PaymentProvider.Checkout result=provider.createCheckout(request);jdbc.update("UPDATE operacao_financeira SET estado='PENDENTE',referencia_externa=?,checkout_url=?,checkout_expires_at=?,updated_at=clock_timestamp() WHERE id=?",result.providerOrderId(),result.url(),java.sql.Timestamp.from(result.expiresAt()),id);return new CheckoutView(result.url(),result.expiresAt(),id);}catch(OrderService.OrderException ex){jdbc.update("UPDATE operacao_financeira SET estado='INCERTA',updated_at=clock_timestamp() WHERE id=?",id);jdbc.update("UPDATE financeiro_outbox SET estado='RECONCILIAR' WHERE operacao_id=? AND estado IN ('PENDENTE','EM_ENVIO')",id);throw ex;}
    }

    @Transactional
    FinancialOperationView reconcile(AuthService.SessionPrincipal principal,UUID id){
        Map<String,Object> row=find(id);authorize(row,principal.userId());if(!"COBRANCA".equals(row.get("tipo")))throw error(HttpStatus.CONFLICT,"PAYMENT_NOT_CHARGE","A operação não é uma cobrança particular.");if(row.get("referencia_externa")==null)throw error(HttpStatus.CONFLICT,"PAYMENT_NOT_CREATED","O checkout ainda não foi criado.");if(!provider.configured())throw error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","O pagamento de teste não está habilitado.");
        UUID operation=(UUID)row.get("id");String beneficiary=String.valueOf(row.get("beneficiario_referencia"));PaymentProvider.CheckoutRequest req=new PaymentProvider.CheckoutRequest(operation,(BigDecimal)row.get("valor"),String.valueOf(row.get("moeda")),"exameperto:"+operation+":"+beneficiary,beneficiary,payerEmail);PaymentProvider.PaymentEvent event=provider.query(String.valueOf(row.get("referencia_externa")),req);try{orders.applyPaymentEvent(event,hash(event));}catch(OrderService.OrderException ex){throw ex;}return financial(principal.userId(),id);
    }

    FinancialOperationView financial(UUID actor,UUID id){Map<String,Object> row=find(id);authorize(row,actor);return new FinancialOperationView((UUID)row.get("id"),(UUID)row.get("pedido_id"),(String)row.get("tipo"),(BigDecimal)row.get("valor"),(String)row.get("moeda"),(String)row.get("estado"),((Number)row.get("version")).longValue());}
    private Map<String,Object> find(UUID id){try{return jdbc.queryForMap("SELECT o.* FROM operacao_financeira o WHERE o.id=?",id);}catch(EmptyResultDataAccessException e){throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Operação financeira não encontrada.");}}
    private Map<String,Object> lock(UUID id){return jdbc.queryForMap("SELECT * FROM operacao_financeira WHERE id=? FOR UPDATE",id);}
    private void authorize(Map<String,Object> row,UUID actor){if(!"COBRANCA".equals(row.get("tipo"))||jdbc.queryForObject("SELECT count(*) FROM pedido p JOIN paciente pa ON pa.id=p.paciente_id WHERE p.id=? AND pa.usuario_id=?",Long.class,row.get("pedido_id"),actor)!=1)throw error(HttpStatus.NOT_FOUND,"NOT_FOUND","Operação financeira não encontrada.");}
    private CheckoutView view(Map<String,Object> row){return new CheckoutView((String)row.get("checkout_url"),((java.sql.Timestamp)row.get("checkout_expires_at")).toInstant(),(UUID)row.get("id"));}
    private byte[] hash(PaymentProvider.PaymentEvent event){try{return MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(event));}catch(Exception e){throw new IllegalStateException(e);}}
    private static OrderService.OrderException error(HttpStatus status,String code,String message){return OrderService.error(status,code,message);}
}
