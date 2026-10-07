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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
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
class VehicleLinkFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    static final Path TEST_ROOT=Path.of(System.getProperty("java.io.tmpdir"),"exame-perto-vehicle-test-"+UUID.randomUUID());
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("storage.private-root",TEST_ROOT::toString);}
    @LocalServerPort int port; @MockBean EmailGateway email; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc; @Autowired DataSource dataSource;
    @AfterAll static void cleanup() throws Exception {if(Files.exists(TEST_ROOT))try(var paths=Files.walk(TEST_ROOT)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
    final HttpClient client=HttpClient.newHttpClient(); final ConcurrentHashMap<String,String> mail=new ConcurrentHashMap<>(); static final String PASS="Senha-sintetica-123";
    @BeforeEach void setup(){when(email.configured()).thenReturn(true);doAnswer(i->{mail.put(i.getArgument(0),i.getArgument(2));return null;}).when(email).send(anyString(),anyString(),anyString());}

    @Test void ownershipReuseReplacementConcurrencyAndReviewGuards() throws Exception {
        Account owner=active("vehicle-owner"),other=active("vehicle-other"),analyst=active("vehicle-analyst");
        createDeliverer(owner); createDeliverer(other);
        String vehicle="""
            {"plate":"ABC-1D23","make":"Honda","model":"CG 160","color":"Vermelha","manufacturingYear":2024,"modelYear":2025,"linkType":"AUTORIZACAO","validUntil":"2027-10-05"}
            """;
        var created=request(owner,"POST","/me/vehicle-links",vehicle,"application/json"); assertThat(created.statusCode()).isEqualTo(201);
        Map<String,Object> link=body(created); String linkId=link.get("id").toString(); assertThat(link.get("status")).isEqualTo("RASCUNHO");
        assertThat(request(other,"GET","/me/vehicle-links/"+linkId,null,"application/json").statusCode()).isEqualTo(404);
        assertThat(request(other,"PUT","/me/vehicle-links/"+linkId+"?version=0",vehicle,"application/json").statusCode()).isEqualTo(404);
        String privileged=vehicle.substring(0,vehicle.length()-2)+",\"status\":\"APROVADO\"}";
        assertThat(request(owner,"POST","/me/vehicle-links",privileged,"application/json").statusCode()).isEqualTo(400);

        byte[] pdf="%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%EOF".getBytes();
        var otherDocument=delivererMultipart(other,"VEICULO","other.pdf",pdf); assertThat(otherDocument.statusCode()).isEqualTo(201);
        String otherDocumentId=body(otherDocument).get("id").toString();
        String badReuse=json.writeValueAsString(Map.of("documentId",otherDocumentId,"purpose","CRLV","linkVersion",0));
        assertThat(request(owner,"POST","/me/vehicle-links/"+linkId+"/documents/reuse",badReuse,"application/json").statusCode()).isEqualTo(404);

        var crlv=vehicleMultipart(owner,linkId,0,"CRLV","crlv.pdf",pdf); assertThat(crlv.statusCode()).isEqualTo(200); link=body(crlv); assertThat(link.get("version")).isEqualTo(1);
        var photo=vehicleMultipart(owner,linkId,1,"FOTO","photo.jpg",jpeg()); assertThat(photo.statusCode()).isEqualTo(200); link=body(photo); assertThat(link.get("version")).isEqualTo(2);
        assertThat(vehicleMultipart(owner,linkId,1,"USO_AUTORIZADO","stale.pdf",pdf).statusCode()).isEqualTo(412);
        var authorization=vehicleMultipart(owner,linkId,2,"USO_AUTORIZADO","authorization.pdf",pdf); assertThat(authorization.statusCode()).isEqualTo(200); link=body(authorization); assertThat(link.get("evidenceComplete")).isEqualTo(true);
        assertThat(request(owner,"GET","/me/vehicle-links",null,"application/json").body()).contains("ABC1D23").contains("USO_AUTORIZADO");

        jdbc.update("INSERT INTO papel_global(usuario_id,papel) SELECT id,'ANALISTA_OPERACIONAL' FROM usuario WHERE email_busca=?",lookup(analyst.email));
        List<Map<String,Object>> queue=list(request(analyst,"GET","/analyst/vehicle-reviews",null,"application/json"));
        Map<String,Object> current=queue.stream().filter(x->linkId.equals(x.get("linkId"))).findFirst().orElseThrow(); String reviewId=current.get("id").toString();
        assertThat(request(analyst,"POST","/analyst/vehicle-reviews/"+reviewId+"/assign",null,"application/json").statusCode()).isEqualTo(403);
        mfa(analyst);
        assertThat(request(analyst,"POST","/analyst/vehicle-reviews/"+reviewId+"/assign",null,"application/json").statusCode()).isEqualTo(200);
        assertThat(request(owner,"POST","/analyst/vehicle-reviews/"+reviewId+"/decision","{\"decision\":\"APROVAR\"}","application/json").statusCode()).isEqualTo(403);

        var replacement=vehicleMultipart(owner,linkId,3,"CRLV","new-crlv.pdf",pdf); assertThat(replacement.statusCode()).isEqualTo(200);
        assertThat(request(analyst,"POST","/analyst/vehicle-reviews/"+reviewId+"/decision","{\"decision\":\"APROVAR\"}","application/json").statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vinculo_documento WHERE vinculo_id=? AND finalidade='CRLV'",Long.class,UUID.fromString(linkId))).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vinculo_documento WHERE vinculo_id=? AND finalidade='CRLV' AND substituido_em IS NULL",Long.class,UUID.fromString(linkId))).isEqualTo(1);

        Map<String,Object> fresh=list(request(analyst,"GET","/analyst/vehicle-reviews",null,"application/json")).stream().filter(x->linkId.equals(x.get("linkId"))).findFirst().orElseThrow(); String freshId=fresh.get("id").toString();
        assertThat(request(analyst,"POST","/analyst/vehicle-reviews/"+freshId+"/assign",null,"application/json").statusCode()).isEqualTo(200);
        for(Map<String,Object> document:(List<Map<String,Object>>)fresh.get("documents")){
            String documentId=document.get("id").toString();
            assertThat(request(analyst,"POST","/analyst/vehicle-reviews/"+freshId+"/documents/"+documentId+"/inspection",null,"application/json").statusCode()).isEqualTo(200);
            assertThat(request(analyst,"GET","/analyst/vehicle-reviews/"+freshId+"/documents/"+documentId+"/download",null,"application/octet-stream").statusCode()).isEqualTo(200);
        }
        var blocked=request(analyst,"POST","/analyst/vehicle-reviews/"+freshId+"/decision","{\"decision\":\"APROVAR\",\"reason\":\"synthetic\"}","application/json");
        assertThat(blocked.statusCode()).isEqualTo(422); assertThat(blocked.body()).contains("POLICY_UNDEFINED");
        var refresh=publicPost("refresh",Map.of("refreshToken",analyst.refresh)); Account rotated=new Account(analyst.email,body(refresh).get("accessToken").toString(),body(refresh).get("refreshToken").toString());
        assertThat(request(analyst,"POST","/analyst/vehicle-reviews/"+freshId+"/decision","{\"decision\":\"APROVAR\"}","application/json").statusCode()).isEqualTo(401);
        assertThat(request(rotated,"POST","/analyst/vehicle-reviews/"+freshId+"/decision","{\"decision\":\"APROVAR\"}","application/json").statusCode()).isEqualTo(403);
        assertThat(request(rotated,"POST","/auth/logout",null,"application/json").statusCode()).isEqualTo(204);
        assertThat(request(rotated,"GET","/analyst/vehicle-reviews",null,"application/json").statusCode()).isEqualTo(401);
    }

    @Test void currentSchemaMigratesFreshDatabaseAndUpgradesExistingV5Schema() {
        String schema="vehicle_v6_upgrade_"+UUID.randomUUID().toString().replace("-","");
        jdbc.execute("CREATE SCHEMA \""+schema+"\"");
        Flyway before=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target(MigrationVersion.fromVersion("5")).load();
        before.migrate();
        assertThat(jdbc.queryForObject("SELECT max(version) FROM \""+schema+"\".flyway_schema_history",String.class)).isEqualTo("5");
        Flyway upgrade=Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").load();
        upgrade.migrate();
        assertThat(jdbc.queryForObject("SELECT max(version::integer) FROM \""+schema+"\".flyway_schema_history",Integer.class)).isEqualTo(14);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name='vinculo_veiculo'",Long.class,schema)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_name='posicao_tarefa'",Long.class,schema)).isEqualTo(1);
        jdbc.execute("DROP SCHEMA \""+schema+"\" CASCADE");
    }

    private void createDeliverer(Account account)throws Exception{assertThat(request(account,"POST","/me/deliverer","{\"birthDate\":\"1990-01-01\"}","application/json").statusCode()).isEqualTo(201);}
    private void mfa(Account account)throws Exception{var enrollment=request(account,"POST","/me/mfa/totp/enrollment",json.writeValueAsString(Map.of("password",PASS)),"application/json");String secret=body(enrollment).get("secret").toString();String code=MfaService.totp(MfaService.decodeBase32(secret),Instant.now().getEpochSecond()/30);assertThat(request(account,"POST","/me/mfa/totp/confirmation",json.writeValueAsString(Map.of("code",code)),"application/json").statusCode()).isEqualTo(200);}
    private Account active(String prefix)throws Exception{String e=prefix+UUID.randomUUID()+"@example.test";var reg=publicPost("register",Map.of("name","Sintético","email",e,"password",PASS));assertThat(reg.statusCode()).isEqualTo(202);String token=mail.get(e).substring(mail.get(e).lastIndexOf(':')+2).trim();assertThat(publicPost("verification",Map.of("token",token)).statusCode()).isEqualTo(200);var login=publicPost("login",Map.of("email",e,"password",PASS,"client","MOBILE"));return new Account(e,body(login).get("accessToken").toString(),body(login).get("refreshToken").toString());}
    private HttpResponse<String> publicPost(String p,Object b)throws Exception{return request(null,"POST","/auth/"+p,json.writeValueAsString(b),"application/json");}
    private HttpResponse<String> request(Account a,String m,String p,String b,String ct)throws Exception{var x=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+p)).header("Content-Type",ct);if(a!=null)x.header("Authorization","Bearer "+a.access);return client.send(x.method(m,b==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(b)).build(),HttpResponse.BodyHandlers.ofString());}
    private HttpResponse<String> delivererMultipart(Account a,String category,String name,byte[] bytes)throws Exception{return multipart(a,"/me/deliverer/documents",Map.of("category",category),name,"application/pdf",bytes);}
    private HttpResponse<String> vehicleMultipart(Account a,String link,long version,String purpose,String name,byte[] bytes)throws Exception{return multipart(a,"/me/vehicle-links/"+link+"/documents",Map.of("version",String.valueOf(version),"purpose",purpose),name,name.endsWith(".jpg")?"image/jpeg":"application/pdf",bytes);}
    private HttpResponse<String> multipart(Account a,String path,Map<String,String> fields,String name,String mime,byte[] bytes)throws Exception{String boundary="----synthetic"+UUID.randomUUID();var out=new java.io.ByteArrayOutputStream();for(var field:fields.entrySet())out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field.getKey()+"\"\r\n\r\n"+field.getValue()+"\r\n").getBytes());out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: "+mime+"\r\n\r\n").getBytes());out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes());var x=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).header("Content-Type","multipart/form-data; boundary="+boundary).header("Authorization","Bearer "+a.access);return client.send(x.POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray())).build(),HttpResponse.BodyHandlers.ofString());}
    private Map<String,Object> body(HttpResponse<String> r)throws Exception{return json.readValue(r.body(),new TypeReference<>(){});}
    private List<Map<String,Object>> list(HttpResponse<String> r)throws Exception{assertThat(r.statusCode()).isEqualTo(200);return json.readValue(r.body(),new TypeReference<>(){});}
    private byte[] lookup(String email){try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(java.util.Base64.getDecoder().decode("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="),"HmacSHA256"));return mac.doFinal(email.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new RuntimeException(e);}}
    private byte[] jpeg(){return new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff,0,1,2,(byte)0xff,(byte)0xd9};}
    record Account(String email,String access,String refresh){}
}
