package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@org.springframework.test.annotation.DirtiesContext
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"registration.privacy-approved=true","security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=","security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=","registration.attempts-per-minute=1000","registration.global-attempts-per-minute=5000"})
class OrderFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    static final Path ROOT=Path.of(System.getProperty("java.io.tmpdir"),"exame-perto-order-test-"+UUID.randomUUID());
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("storage.private-root",ROOT::toString);}
    @LocalServerPort int port; @MockBean EmailGateway email; @MockBean RouteProvider routes; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    final HttpClient client=HttpClient.newHttpClient(); static final String PASS="Senha-sintetica-123"; final java.util.concurrent.ConcurrentHashMap<String,String> mail=new java.util.concurrent.ConcurrentHashMap<>();
    @BeforeEach void setup(){org.mockito.Mockito.when(email.configured()).thenReturn(true);org.mockito.Mockito.doAnswer(i->{mail.put(i.getArgument(0),i.getArgument(2));return null;}).when(email).send(org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.anyString());}
    @AfterAll static void cleanup() throws Exception{if(Files.exists(ROOT))try(var p=Files.walk(ROOT)){p.sorted(java.util.Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(Exception ignored){}});}}

    @Test void orderScopesPrivateAddressesRouteTariffAndVersioning() throws Exception {
        Account patient=active("order-patient"), family=active("order-family"), outsider=active("order-outsider");
        var patientCreated=request(patient,"POST","/me/patient",json.writeValueAsString(Map.of("cpf","12345678901","birthDate","1980-01-01")),null,null);assertThat(patientCreated.statusCode()).isEqualTo(201);UUID patientId=UUID.fromString(body(patientCreated).get("id").toString());
        UUID patientUser=user(patient.email),familyUser=user(family.email),invitation=UUID.randomUUID(),grant=UUID.randomUUID();
        jdbc.update("INSERT INTO convite_familiar(id,paciente_id,destinatario_email_busca,token_hash,estado,expira_em,consumido_em,aceito_por,aceito_em) VALUES (?,?,?,?,'CONFIRMADO',clock_timestamp()+interval '2 days',clock_timestamp(),?,clock_timestamp())",invitation,patientId,lookup(family.email),lookup("order-token"),familyUser);
        jdbc.update("INSERT INTO autorizacao_paciente(id,paciente_id,familiar_id,concedida_por,convite_id,confirmada_em,expira_em) VALUES (?,?,?,?,?,clock_timestamp(),clock_timestamp()+interval '2 days')",grant,patientId,familyUser,user(patient.email),invitation);
        jdbc.update("INSERT INTO autorizacao_escopo(autorizacao_id,escopo) VALUES (?, 'PEDIDOS'), (?, 'RECEBIMENTO')",grant,grant);
        UUID doc=UUID.randomUUID();jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?, 'AUTORIZACAO_RETIRADA',?,'12345678901234567890123456789012','application/pdf',100,'QUARENTENA')",doc,familyUser,"synthetic/"+doc);
        Map<String,Object> input=Map.of("patientId",patientId,"recipientUserId",user(patient.email),"pickupUnitId",UUID.randomUUID(),"pickupAuthorizationDocumentId",doc,"origin",address("01001000",-23.55,-46.63),"destination",address("20040002",-22.90,-43.17));
        assertThat(request(outsider,"POST","/orders",json.writeValueAsString(input),null,null).statusCode()).isEqualTo(404);
        var created=request(family,"POST","/orders",json.writeValueAsString(input),"order-1",null);assertThat(created.statusCode()).as(created.body()).isEqualTo(201);UUID order=UUID.fromString(body(created).get("id").toString());assertThat(body(created).get("status")).isEqualTo("EM_VERIFICACAO");
        assertThat(request(outsider,"GET","/orders/"+order,null,null,null).statusCode()).isEqualTo(404);assertThat(request(family,"GET","/orders/"+order+"/addresses",null,null,null).statusCode()).isEqualTo(200);
        assertThat(request(family,"POST","/orders/"+order+"/quotes","{}",null,"0").statusCode()).isEqualTo(422);
        jdbc.update("UPDATE autorizacao_retirada SET estado='VERIFICADA' WHERE pedido_id=?",order);
        when(routes.route(any(),any())).thenReturn(new RouteProvider.RouteResult("synthetic-router","route-001",12500,1800,true,Instant.now()));
        UUID tariff=UUID.randomUUID();jdbc.update("INSERT INTO tarifa(id,numero,formula_codigo,parametros,inicio,estado) VALUES (?,?,?,'{\"base\":\"10.00\",\"perKm\":\"2.00\",\"perMinute\":\"0.10\",\"minimum\":\"5.00\",\"validitySeconds\":900}'::jsonb,clock_timestamp(),'ATIVA')",tariff,7,"BASE_KM_MINUTO");
        var quoted=request(family,"POST","/orders/"+order+"/quotes","{}","quote-1","0");assertThat(quoted.statusCode()).as(quoted.body()).isEqualTo(201);UUID quote=UUID.fromString(body(quoted).get("id").toString());assertThat(body(quoted).get("grossAmount")).isEqualTo(38.00);assertThat(request(family,"POST","/orders/"+order+"/quotes","{}","quote-1","0").statusCode()).isEqualTo(201);
        assertThat(request(family,"POST","/orders/"+order+"/quotes","{}","quote-stale","0").statusCode()).isEqualTo(412);
        var changed=request(family,"PUT","/orders/"+order+"/addresses",json.writeValueAsString(Map.of("origin",address("01001000",-23.50,-46.60),"destination",address("20040002",-22.80,-43.10),"version",1)),null,null);assertThat(changed.statusCode()).isEqualTo(200);assertThat(request(family,"GET","/quotes/"+quote,null,null,null).statusCode()).isEqualTo(200);assertThat(body(request(family,"GET","/quotes/"+quote,null,null,null)).get("status")).isEqualTo("SUBSTITUIDO");
        when(routes.route(any(),any())).thenReturn(new RouteProvider.RouteResult("synthetic-router","route-002",12500,1800,false,Instant.now()));var expiring=request(family,"POST","/orders/"+order+"/quotes","{}","quote-expiring","2");assertThat(expiring.statusCode()).isEqualTo(201);UUID expiringId=UUID.fromString(body(expiring).get("id").toString());jdbc.update("UPDATE orcamento SET expira_em=clock_timestamp()-interval '1 second' WHERE id=?",expiringId);assertThat(body(request(family,"GET","/quotes/"+expiringId,null,null,null)).get("status")).isEqualTo("EXPIRADO");
        when(routes.route(any(),any())).thenReturn(new RouteProvider.RouteResult("synthetic-router","bad",0,1,false,Instant.now()));assertThat(request(family,"POST","/orders/"+order+"/quotes","{}","quote-invalid","3").statusCode()).isEqualTo(422);
        when(routes.route(any(),any())).thenThrow(OrderService.error(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","timeout"));assertThat(request(family,"POST","/orders/"+order+"/quotes","{}","quote-timeout","3").statusCode()).isEqualTo(503);
        jdbc.update("UPDATE autorizacao_paciente SET revogada_em=clock_timestamp() WHERE id=?",grant);assertThat(request(family,"POST","/orders",json.writeValueAsString(input),"order-revoked",null).statusCode()).isEqualTo(404);
    }
    private Map<String,Object> address(String postal,double lat,double lon){return Map.of("street","Rua sintética","number","10","district","Centro","city","Cidade","state","SP","postalCode",postal,"latitude",lat,"longitude",lon);}
    private byte[] pdf(){return "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%EOF".getBytes();}
    private HttpResponse<String> upload(Account a,byte[] bytes)throws Exception{String b="----ord"+UUID.randomUUID();ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(("--"+b+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"authorization.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes());out.write(bytes);out.write(("\r\n--"+b+"--\r\n").getBytes());return requestBytes(a,"POST","/order-documents/pickup-authorization",out.toByteArray(),"multipart/form-data; boundary="+b,null,null);}
    private Account active(String prefix)throws Exception{String e=prefix+UUID.randomUUID()+"@example.test";var r=request(null,"POST","/auth/register",json.writeValueAsString(Map.of("name","Sintético","email",e,"password",PASS)),null,null);assertThat(r.statusCode()).as(r.body()).isEqualTo(202);String sent=mail.get(e);String token=sent.substring(sent.lastIndexOf(':')+2).trim();assertThat(request(null,"POST","/auth/verification",json.writeValueAsString(Map.of("token",token)),null,null).statusCode()).isEqualTo(200);var login=request(null,"POST","/auth/login",json.writeValueAsString(Map.of("email",e,"password",PASS,"client","MOBILE")),null,null);assertThat(login.statusCode()).as(login.body()).isEqualTo(200);return new Account(e,body(login).get("accessToken").toString());}
    private UUID user(String email){return jdbc.queryForObject("SELECT id FROM usuario WHERE email_busca=?",UUID.class,lookup(email));}
    private HttpResponse<String> request(Account a,String method,String path,String body,String idem,String etag)throws Exception{return requestBytes(a,method,path,body==null?null:body.getBytes(),"application/json",idem,etag);}
    private HttpResponse<String> requestBytes(Account a,String method,String path,byte[] body,String type,String idem,String etag)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).header("Content-Type",type);if(a!=null)b.header("Authorization","Bearer "+a.access);if(idem!=null)b.header("Idempotency-Key",idem);if(etag!=null)b.header("If-Match",etag);return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofString());}
    private Map<String,Object> body(HttpResponse<String> r)throws Exception{return json.readValue(r.body(),new TypeReference<>(){});}
    private byte[] lookup(String value){try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(java.util.Base64.getDecoder().decode("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="),"HmacSHA256"));return mac.doFinal(value.getBytes());}catch(Exception e){throw new RuntimeException(e);}}
    record Account(String email,String access){}
}
