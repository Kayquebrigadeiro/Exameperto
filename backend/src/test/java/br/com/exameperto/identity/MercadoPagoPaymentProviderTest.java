package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MercadoPagoPaymentProviderTest {
    HttpServer server;
    @AfterEach void stop(){if(server!=null)server.stop(0);}
    @Test void checkoutQueryAndAuthenticatedWebhookUseStableReference() throws Exception {
        UUID operation=UUID.randomUUID(),beneficiary=UUID.randomUUID();String reference="exameperto:"+operation+":"+beneficiary;server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/orders",e->{String body="{\"id\":\"ORDTST1\",\"checkout_url\":\"https://sandbox.mercadopago.test/ORDTST1\",\"status\":\"created\",\"total_paid_amount\":\"0.00\"}";e.sendResponseHeaders(201,body.length());e.getResponseBody().write(body.getBytes());e.close();});
        server.createContext("/v1/orders/ORDTST1",e->{String body="{\"id\":\"ORDTST1\",\"status\":\"created\",\"total_paid_amount\":\"0.00\"}";e.sendResponseHeaders(200,body.length());e.getResponseBody().write(body.getBytes());e.close();});
        server.createContext("/v1/payments/123",e->{String body="{\"external_reference\":\""+reference+"\",\"status\":\"approved\",\"transaction_amount\":\"38.00\",\"currency_id\":\"BRL\"}";e.sendResponseHeaders(200,body.length());e.getResponseBody().write(body.getBytes());e.close();});
        server.start();
        MercadoPagoPaymentProvider provider=new MercadoPagoPaymentProvider(true,"SANDBOX","test-token","secret",URI.create("http://127.0.0.1:"+server.getAddress().getPort()),Duration.ofSeconds(2),65536,new ObjectMapper());
        PaymentProvider.CheckoutRequest request=new PaymentProvider.CheckoutRequest(operation,new BigDecimal("38.00"),"BRL",reference,beneficiary.toString(),"buyer@testuser.com");PaymentProvider.Checkout checkout=provider.createCheckout(request);assertThat(checkout.providerOrderId()).isEqualTo("ORDTST1");assertThat(checkout.url()).startsWith("https://sandbox");assertThat(provider.query("ORDTST1",request).result()).isEqualTo(PaymentProvider.Result.UNCERTAIN);
        String payload="{\"type\":\"payment\",\"data\":{\"id\":\"123\"}}";String signature="ts=1700000000,v1="+hmac("id:123;request-id:req-1;ts:1700000000;","secret");PaymentProvider.PaymentEvent event=provider.authenticateAndParse(payload.getBytes(StandardCharsets.UTF_8),Map.of("x-signature",signature,"x-request-id","req-1"));assertThat(event.operationId()).isEqualTo(operation);assertThat(event.result()).isEqualTo(PaymentProvider.Result.CONFIRMED);assertThat(event.beneficiaryReference()).isEqualTo(beneficiary.toString());
    }
    @Test void invalidSignatureIsRejectedWithoutProviderEffect() throws Exception {
        MercadoPagoPaymentProvider provider=new MercadoPagoPaymentProvider(true,"SANDBOX","test-token","secret",URI.create("http://127.0.0.1:1"),Duration.ofSeconds(1),65536,new ObjectMapper());assertThatThrownBy(()->provider.authenticateAndParse("{\"data\":{\"id\":\"1\"}}".getBytes(),Map.of("x-signature","ts=1,v1=bad","x-request-id","x"))).isInstanceOf(OrderService.OrderException.class).hasMessageContaining("Notificação");
    }
    private static String hmac(String value,String secret)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));StringBuilder out=new StringBuilder();for(byte b:mac.doFinal(value.getBytes(StandardCharsets.UTF_8)))out.append(String.format("%02x",b));return out.toString();}
}
