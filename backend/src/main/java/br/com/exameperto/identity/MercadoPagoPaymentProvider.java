package br.com.exameperto.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Primary;

@Component @Primary
final class MercadoPagoPaymentProvider implements PaymentProvider {
    private final boolean enabled; private final String token; private final String secret; private final URI base;
    private final Duration timeout; private final int maxBytes; private final HttpClient client; private final ObjectMapper json;
    MercadoPagoPaymentProvider(@Value("${payment.mercadopago.enabled:false}") boolean enabled,
        @Value("${payment.mercadopago.environment:SANDBOX}") String environment,
        @Value("${payment.mercadopago.access-token:}") String token,
        @Value("${payment.mercadopago.webhook-secret:}") String secret,
        @Value("${payment.mercadopago.api-base-url:https://api.mercadopago.com}") URI base,
        @Value("${payment.mercadopago.timeout:PT5S}") Duration timeout,
        @Value("${payment.mercadopago.max-response-bytes:65536}") int maxBytes,ObjectMapper json){
        this.enabled=enabled&&"SANDBOX".equalsIgnoreCase(environment)&&!token.isBlank();this.token=token.trim();this.secret=secret.trim();this.base=base;this.timeout=timeout;this.maxBytes=maxBytes;this.json=json;this.client=HttpClient.newBuilder().connectTimeout(timeout).build();
        if(!base.getScheme().equalsIgnoreCase("https")&&!base.getHost().equals("127.0.0.1")&&!base.getHost().equals("localhost"))throw new IllegalArgumentException("payment.mercadopago.api-base-url must use HTTPS outside local tests");
    }
    public boolean configured(){return enabled;}
    public String providerId(){return "mercadopago-sandbox";}
    public Checkout createCheckout(CheckoutRequest r){
        requireConfigured(); Map<String,Object> body=new HashMap<>();body.put("type","online");body.put("total_amount",r.amount().setScale(2).toPlainString());body.put("external_reference",r.externalReference());body.put("processing_mode","manual");body.put("capture_mode","automatic_async");body.put("expiration_time","P1D");body.put("items",java.util.List.of(Map.of("title","Entrega Exame Perto","quantity",1,"unit_price",r.amount().setScale(2),"currency_id",r.currency())));
        if(r.payerEmail()!=null&&!r.payerEmail().isBlank())body.put("payer",Map.of("email",r.payerEmail()));
        JsonNode node=send("POST","/v1/orders",json.valueToTree(body),"checkout:"+r.operationId());String id=text(node,"id"),url=text(node,"checkout_url");if(id.isBlank()||url.isBlank())throw unavailable("A resposta do checkout não trouxe identificador e URL.");Instant expiry=Instant.now().plusSeconds(86400);if(!text(node,"expiration_time").isBlank())try{expiry=Instant.parse(text(node,"expiration_time"));}catch(Exception ignored){}return new Checkout(id,url,expiry);
    }
    public PaymentEvent query(String providerOrderId,CheckoutRequest r){
        JsonNode node=send("GET","/v1/orders/"+java.net.URLEncoder.encode(providerOrderId,StandardCharsets.UTF_8),null,null);String status=text(node,"status");Result result=switch(status){case "processed"->Result.CONFIRMED;case "cancelled","expired","failed"->Result.FAILED;default->Result.UNCERTAIN;};String currency=text(node,"currency");if(currency.isBlank())currency=text(node,"currency_id");return new PaymentEvent(providerOrderId,r.operationId(),result,decimal(node,"total_paid_amount",r.amount()),currency.isBlank()?r.currency():currency,r.beneficiaryReference(),Instant.now());
    }
    public PaymentEvent authenticateAndParse(byte[] body,Map<String,String> headers){
        requireConfigured();String signature=headers.getOrDefault("x-signature","");String requestId=headers.getOrDefault("x-request-id","");JsonNode incoming;try{incoming=json.readTree(body);}catch(Exception e){throw invalid();}String dataId=incoming.path("data").path("id").asText("");String ts=part(signature,"ts"),v1=part(signature,"v1");if(dataId.isBlank()||ts.isBlank()||v1.isBlank()||secret.isBlank())throw invalid();String manifest="id:"+dataId+";request-id:"+requestId+";ts:"+ts+";";if(!constant(hmac(manifest),v1))throw invalid();JsonNode payment=send("GET","/v1/payments/"+java.net.URLEncoder.encode(dataId,StandardCharsets.UTF_8),null,null);String ref=text(payment,"external_reference");String[] parts=ref.split(":");if(parts.length!=3||!"exameperto".equals(parts[0]))throw invalid();try{UUID operation=UUID.fromString(parts[1]);UUID beneficiary=UUID.fromString(parts[2]);Result result=switch(text(payment,"status")){case "approved"->Result.CONFIRMED;case "rejected","cancelled","refunded","charged_back"->Result.FAILED;default->Result.UNCERTAIN;};return new PaymentEvent(dataId,operation,result,decimal(payment,"transaction_amount",BigDecimal.ZERO),text(payment,"currency_id"),beneficiary.toString(),Instant.now());}catch(Exception e){throw invalid();}
    }
    private JsonNode send(String method,String path,JsonNode body,String idem){try{HttpRequest.Builder b=HttpRequest.newBuilder(base.resolve(path)).timeout(timeout).header("Authorization","Bearer "+token).header("Accept","application/json");if(body!=null)b.header("Content-Type","application/json");if(idem!=null)b.header("X-Idempotency-Key",idem);HttpRequest req=method.equals("GET")?b.GET().build():b.method(method,HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();HttpResponse<byte[]> response=client.send(req,HttpResponse.BodyHandlers.ofByteArray());if(response.body().length>maxBytes)throw unavailable("Resposta do provedor excedeu o limite permitido.");if(response.statusCode()<200||response.statusCode()>=300)throw unavailable("O provedor de pagamento recusou ou não respondeu normalmente.");return json.readTree(response.body());}catch(OrderService.OrderException e){throw e;}catch(Exception e){throw unavailable("O provedor de pagamento está indisponível.");}}
    private void requireConfigured(){if(!configured())throw unavailable("O pagamento de teste não está habilitado.");}
    private static OrderService.OrderException unavailable(String m){return OrderService.error(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE",m);}
    private static OrderService.OrderException invalid(){return OrderService.error(HttpStatus.UNAUTHORIZED,"INVALID_SIGNATURE","Notificação de pagamento não autenticada.");}
    private static String text(JsonNode n,String k){return n.path(k).asText("");}
    private static BigDecimal decimal(JsonNode n,String k,BigDecimal fallback){try{return n.path(k).decimalValue();}catch(Exception e){return fallback;}}
    private static String part(String value,String key){for(String p:value.split(",")){String[] pair=p.trim().split("=",2);if(pair.length==2&&pair[0].equals(key))return pair[1];}return "";}
    private String hmac(String value){try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));byte[] out=mac.doFinal(value.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte b:out)s.append(String.format("%02x",b));return s.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    private static boolean constant(String a,String b){return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8),b.getBytes(StandardCharsets.UTF_8));}
}
