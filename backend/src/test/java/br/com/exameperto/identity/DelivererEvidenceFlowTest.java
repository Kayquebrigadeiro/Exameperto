package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
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
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "registration.privacy-approved=true", "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=", "registration.attempts-per-minute=1000", "registration.global-attempts-per-minute=5000"})
class DelivererEvidenceFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    static final Path TEST_ROOT=Path.of(System.getProperty("java.io.tmpdir"),"exame-perto-deliverer-test-"+UUID.randomUUID());
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("storage.private-root",TEST_ROOT::toString);}
    @LocalServerPort int port; @MockBean EmailGateway email; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @AfterAll static void cleanup() throws Exception { if(Files.exists(TEST_ROOT)) try(var paths=Files.walk(TEST_ROOT)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});} }
    final HttpClient client=HttpClient.newHttpClient(); final java.util.concurrent.ConcurrentHashMap<String,String> mail=new java.util.concurrent.ConcurrentHashMap<>(); static final String PASS="Senha-sintetica-123";
    @BeforeEach void setup(){when(email.configured()).thenReturn(true);doAnswer(i->{mail.put(i.getArgument(0),i.getArgument(2));return null;}).when(email).send(anyString(),anyString(),anyString());}
    @Test void quarantineReplacementProxyAndReviewerGuards() throws Exception {
        Account owner=active("deliverer"), other=active("other");
        var created=request(owner,"POST","/me/deliverer",json.writeValueAsString(Map.of("birthDate","1990-01-01")),"application/json"); assertThat(created.statusCode()).isEqualTo(201);
        byte[] pdf="%PDF-synthetic-file".getBytes(); var upload=multipart(owner,"HABILITACAO","doc.pdf","application/pdf",pdf); assertThat(upload.statusCode()).isEqualTo(201); UUID id=UUID.fromString(body(upload).get("id").toString());
        assertThat(request(owner,"GET","/documents/"+id+"/download",null,"application/json").statusCode()).isEqualTo(404);
        assertThat(multipart(owner,"HABILITACAO","fake.pdf","application/pdf","not-a-pdf".getBytes()).statusCode()).isEqualTo(422);
        assertThat(multipart(owner,"HABILITACAO","huge.pdf","application/pdf",new byte[10*1024*1024+1]).statusCode()).isEqualTo(413);
        var replacement=multipart(owner,"HABILITACAO","new.pdf","application/pdf",pdf); assertThat(replacement.statusCode()).isEqualTo(201); assertThat(jdbc.queryForObject("SELECT count(*) FROM documento WHERE proprietario_id=(SELECT id FROM usuario WHERE email_busca=? )",Long.class,new DataProtectorTestLookup().lookup(owner.email))).isEqualTo(2);
        jdbc.update("UPDATE documento SET estado='INSPECAO_APROVADA' WHERE id=?",id);
        var downloaded=request(owner,"GET","/documents/"+id+"/download",null,"application/octet-stream"); assertThat(downloaded.statusCode()).isEqualTo(200); assertThat(downloaded.headers().firstValue("Cache-Control")).contains("no-store"); assertThat(downloaded.body()).isEqualTo(new String(pdf));
        assertThat(request(other,"GET","/documents/"+id+"/download",null,"application/octet-stream").statusCode()).isEqualTo(404);
        assertThat(request(owner,"GET","/analyst/reviews",null,"application/json").statusCode()).isEqualTo(403);
        jdbc.update("INSERT INTO papel_global(usuario_id,papel) SELECT id,'ANALISTA_OPERACIONAL' FROM usuario WHERE email_busca=?",new DataProtectorTestLookup().lookup(owner.email));
        assertThat(request(owner,"GET","/analyst/reviews",null,"application/json").statusCode()).isEqualTo(200);
        assertThat(request(owner,"POST","/analyst/reviews/"+UUID.randomUUID()+"/decision","{\"decision\":\"APROVAR\",\"reason\":\"synthetic\"}","application/json").statusCode()).isEqualTo(403);
        assertThat(Files.exists(TEST_ROOT.resolve("quarantine"))).isTrue();
    }
    private Account active(String prefix)throws Exception{String e=prefix+UUID.randomUUID()+"@example.test";var reg=publicPost("register",Map.of("name","Sintético","email",e,"password",PASS));assertThat(reg.statusCode()).isEqualTo(202);String token=mail.get(e).substring(mail.get(e).lastIndexOf(':')+2).trim();assertThat(publicPost("verification",Map.of("token",token)).statusCode()).isEqualTo(200);var login=publicPost("login",Map.of("email",e,"password",PASS,"client","MOBILE"));return new Account(e,body(login).get("accessToken").toString());}
    private HttpResponse<String> publicPost(String p,Object b)throws Exception{return request(null,"POST","/auth/"+p,json.writeValueAsString(b),"application/json");}
    private HttpResponse<String> request(Account a,String m,String p,String b,String ct)throws Exception{var x=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+p)).header("Content-Type",ct);if(a!=null)x.header("Authorization","Bearer "+a.access);return client.send(x.method(m,b==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(b)).build(),HttpResponse.BodyHandlers.ofString());}
    private HttpResponse<String> multipart(Account a,String category,String name,String mime,byte[] bytes)throws Exception{String boundary="----synthetic"+UUID.randomUUID();String pre="--"+boundary+"\r\nContent-Disposition: form-data; name=\"category\"\r\n\r\n"+category+"\r\n--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+mime+"\r\n\r\n";byte[] p=pre.getBytes(),end=("\r\n--"+boundary+"--\r\n").getBytes();byte[] all=new byte[p.length+bytes.length+end.length];System.arraycopy(p,0,all,0,p.length);System.arraycopy(bytes,0,all,p.length,bytes.length);System.arraycopy(end,0,all,p.length+bytes.length,end.length);var x=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/me/deliverer/documents")).header("Content-Type","multipart/form-data; boundary="+boundary).header("Authorization","Bearer "+a.access);return client.send(x.POST(HttpRequest.BodyPublishers.ofByteArray(all)).build(),HttpResponse.BodyHandlers.ofString());}
    private Map<String,Object> body(HttpResponse<String> r)throws Exception{return json.readValue(r.body(),new TypeReference<>(){});}
    record Account(String email,String access){}
    // Test-only HMAC helper uses the same configured key; no value is persisted outside the synthetic database.
    static final class DataProtectorTestLookup { byte[] lookup(String email){ try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(java.util.Base64.getDecoder().decode("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="),"HmacSHA256"));return mac.doFinal(email.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new RuntimeException(e);} } }
}
