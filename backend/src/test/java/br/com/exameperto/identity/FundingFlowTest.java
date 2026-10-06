package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "registration.privacy-approved=true", "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=", "registration.attempts-per-minute=1000", "registration.global-attempts-per-minute=5000"})
class FundingFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    static final Path TEST_ROOT=Path.of(System.getProperty("java.io.tmpdir"),"exame-perto-funding-test-"+UUID.randomUUID());
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("storage.private-root",TEST_ROOT::toString);}
    @LocalServerPort int port; @MockBean EmailGateway email; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @AfterAll static void cleanup() throws Exception{if(Files.exists(TEST_ROOT))try(var paths=Files.walk(TEST_ROOT)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
    final HttpClient client=HttpClient.newHttpClient(); final ConcurrentHashMap<String,String> mail=new ConcurrentHashMap<>(); static final String PASS="Senha-sintetica-123";
    @BeforeEach void setup(){when(email.configured()).thenReturn(true);doAnswer(i->{mail.put(i.getArgument(0),i.getArgument(2));return null;}).when(email).send(anyString(),anyString(),anyString());}

    @Test void fundingIsolationMfaIdempotencyConcurrencyAndLedger() throws Exception {
        Account registrar=active("funding-registrar"), reviewer=active("funding-reviewer"), reviewer2=active("funding-reviewer2"), outsider=active("funding-outsider");
        UUID institution=UUID.randomUUID(), program=UUID.randomUUID();
        jdbc.update("INSERT INTO instituicao(id,nome,estado) VALUES (?,?,'HABILITADA')",institution,"Instituição financeira sintética");
        jdbc.update("INSERT INTO programa(id,instituicao_id,codigo,nome,estado) VALUES (?,?,?,'Programa sintético','HABILITADO')",program,institution,"SYNTH-FUND");
        grant(institution,registrar,"GESTOR_FINANCEIRO"); grant(institution,reviewer,"GESTOR_FINANCEIRO"); grant(institution,reviewer2,"GESTOR_FINANCEIRO");
        global(registrar); global(reviewer); global(reviewer2); mfa(registrar);
        byte[] pdf="%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%EOF".getBytes();
        UUID original=UUID.fromString(body(uploadEvidence(registrar,pdf)).get("id").toString());
        String create=json.writeValueAsString(Map.of("amount","100.50","currency","BRL","evidenceDocumentId",original,"sourceReference","bank-ref-001"));
        var missingKey=request(registrar,"POST","/programs/"+program+"/funding",create,null,null); assertThat(missingKey.statusCode()).isEqualTo(400);
        var created=request(registrar,"POST","/programs/"+program+"/funding",create,"idem-001",null); assertThat(created.statusCode()).as(created.body()).isEqualTo(201); UUID funding=UUID.fromString(body(created).get("id").toString());
        assertThat(request(reviewer,"GET","/programs/"+program+"/balance",null,null,null).statusCode()).isEqualTo(403);
        assertThat(request(registrar,"POST","/funding/"+funding+"/review",reviewBody("CONFIRMADO",original,"self"),"review-self","0").statusCode()).isEqualTo(403);
        assertThat(request(reviewer,"POST","/funding/"+funding+"/review",reviewBody("CONFIRMADO",original,"no-mfa"),"review-1","0").statusCode()).isEqualTo(403); mfa(reviewer); assertAmount(balance(reviewer,program),"0");
        UUID reconciliation=UUID.fromString(body(uploadEvidence(reviewer,pdf)).get("id").toString()); jdbc.update("UPDATE documento SET estado='INSPECAO_APROVADA' WHERE id IN (?,?)",original,reconciliation);
        assertThat(request(reviewer,"POST","/funding/"+funding+"/review",reviewBody("CONFIRMADO",original,"wrong-owner"),"review-2","0").statusCode()).isEqualTo(404);
        String confirm=reviewBody("CONFIRMADO",reconciliation,"conciliado"); var confirmed=request(reviewer,"POST","/funding/"+funding+"/review",confirm,"review-3","0"); assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200); assertAmount(balance(reviewer,program),"100.50");
        var replay=request(reviewer,"POST","/funding/"+funding+"/review",confirm,"review-3","0"); assertThat(replay.statusCode()).isEqualTo(200); assertAmount(balance(reviewer,program),"100.50");
        assertThat(request(reviewer2,"GET","/programs/"+program+"/balance",null,null,null).statusCode()).isEqualTo(403); // member role exists only after MFA/nominal global is still required
        mfa(reviewer2);
        UUID secondOriginal=UUID.fromString(body(uploadEvidence(registrar,pdf)).get("id").toString()); var second=request(registrar,"POST","/programs/"+program+"/funding",json.writeValueAsString(Map.of("amount","40.00","currency","BRL","evidenceDocumentId",secondOriginal,"sourceReference","bank-ref-002")),"idem-002",null); UUID secondId=UUID.fromString(body(second).get("id").toString()); UUID secondRecon=UUID.fromString(body(uploadEvidence(reviewer2,pdf)).get("id").toString()); jdbc.update("UPDATE documento SET estado='INSPECAO_APROVADA' WHERE id=?",secondRecon);
        var c1=CompletableFuture.supplyAsync(()->requestUnchecked(reviewer,"POST","/funding/"+secondId+"/review",reviewBody("CONFIRMADO",secondRecon,"concurrent"),"review-c1","0")); var c2=CompletableFuture.supplyAsync(()->requestUnchecked(reviewer2,"POST","/funding/"+secondId+"/review",reviewBody("CONFIRMADO",secondRecon,"concurrent"),"review-c2","0")); assertThat(List.of(c1.get(),c2.get()).stream().filter(x->x.statusCode()==200).count()).isEqualTo(1); assertAmount(balance(reviewer,program),"140.50");
        UUID rejectedOriginal=UUID.fromString(body(uploadEvidence(registrar,pdf)).get("id").toString()); var rejected=request(registrar,"POST","/programs/"+program+"/funding",json.writeValueAsString(Map.of("amount","9.99","currency","BRL","evidenceDocumentId",rejectedOriginal,"sourceReference","bank-ref-003")),"idem-003",null); UUID rejectedId=UUID.fromString(body(rejected).get("id").toString()); UUID rejectedRecon=UUID.fromString(body(uploadEvidence(reviewer,pdf)).get("id").toString()); jdbc.update("UPDATE documento SET estado='INSPECAO_APROVADA' WHERE id=?",rejectedRecon); assertThat(request(reviewer,"POST","/funding/"+rejectedId+"/review",reviewBody("REJEITADO",rejectedRecon,"não conciliado"),"review-rejected","0").statusCode()).isEqualTo(200); assertAmount(balance(reviewer,program),"140.50");
        assertThat(request(outsider,"GET","/programs/"+program+"/balance",null,null,null).statusCode()).isEqualTo(403); assertThat(request(registrar,"POST","/programs/"+program+"/funding",json.writeValueAsString(Map.of("amount","-1","currency","BRL","evidenceDocumentId",original,"sourceReference","bad")),"idem-bad",null).statusCode()).isEqualTo(400); assertThat(request(reviewer,"POST","/funding/"+funding+"/review",confirm,"review-stale","0").statusCode()).isEqualTo(412);
    }

    private void global(Account a){jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES ((SELECT id FROM usuario WHERE email_busca=?),'GESTOR_FINANCEIRO')",lookup(a.email));}
    private void grant(UUID institution,Account a,String role){jdbc.update("INSERT INTO membro_instituicao(instituicao_id,usuario_id,papel) VALUES (?,?,?)",institution,userId(a.email),role);}
    private UUID userId(String email){return jdbc.queryForObject("SELECT id FROM usuario WHERE email_busca=?",UUID.class,lookup(email));}
    private String reviewBody(String decision,UUID evidence,String reason){try{return json.writeValueAsString(Map.of("decision",decision,"reconciliationEvidenceId",evidence,"reasonCode",reason));}catch(Exception ex){throw new RuntimeException(ex);}}
    private Map<String,Object> balance(Account a,UUID program)throws Exception{return body(request(a,"GET","/programs/"+program+"/balance",null,null,null));}
    private void assertAmount(Map<String,Object> value,String expected){assertThat(new java.math.BigDecimal(value.get("available").toString())).isEqualByComparingTo(expected);}
    private HttpResponse<String> uploadEvidence(Account a,byte[] bytes)throws Exception{String boundary="----fund"+UUID.randomUUID();ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"proof.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes());out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes());var response=requestBytes(a,"POST","/me/financial-documents",out.toByteArray(),"multipart/form-data; boundary="+boundary,null,null);assertThat(response.statusCode()).as(response.body()).isEqualTo(201);return response;}
    private Account active(String prefix)throws Exception{String e=prefix+UUID.randomUUID()+"@example.test";var reg=request(null,"POST","/auth/register",json.writeValueAsString(Map.of("name","Sintético","email",e,"password",PASS)),null,null);assertThat(reg.statusCode()).isEqualTo(202);String sent=mail.get(e);String token=sent.substring(sent.lastIndexOf(':')+2).trim();assertThat(request(null,"POST","/auth/verification",json.writeValueAsString(Map.of("token",token)),null,null).statusCode()).isEqualTo(200);var login=request(null,"POST","/auth/login",json.writeValueAsString(Map.of("email",e,"password",PASS,"client","MOBILE")),null,null);return new Account(e,body(login).get("accessToken").toString());}
    private void mfa(Account a)throws Exception{var e=request(a,"POST","/me/mfa/totp/enrollment",json.writeValueAsString(Map.of("password",PASS)),null,null);String secret=body(e).get("secret").toString();String code=MfaService.totp(MfaService.decodeBase32(secret),Instant.now().getEpochSecond()/30);assertThat(request(a,"POST","/me/mfa/totp/confirmation",json.writeValueAsString(Map.of("code",code)),null,null).statusCode()).isEqualTo(200);}
    private HttpResponse<String> request(Account a,String method,String path,String body,String idem,String etag)throws Exception{return requestBytes(a,method,path,body==null?null:body.getBytes(),"application/json",idem,etag);}
    private HttpResponse<String> requestUnchecked(Account a,String method,String path,String body,String idem,String etag){try{return request(a,method,path,body,idem,etag);}catch(Exception ex){throw new RuntimeException(ex);}}
    private HttpResponse<String> requestBytes(Account a,String method,String path,byte[] body,String contentType,String idem,String etag)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).header("Content-Type",contentType);if(a!=null)b.header("Authorization","Bearer "+a.access);if(idem!=null)b.header("Idempotency-Key",idem);if(etag!=null)b.header("If-Match",etag);return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofString());}
    private Map<String,Object> body(HttpResponse<String> r)throws Exception{return json.readValue(r.body(),new TypeReference<>(){});}
    private byte[] lookup(String e){try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(java.util.Base64.getDecoder().decode("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="),"HmacSHA256"));return mac.doFinal(e.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception x){throw new RuntimeException(x);}}
    record Account(String email,String access){}
}
