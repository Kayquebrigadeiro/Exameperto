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
class BenefitFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    static final Path TEST_ROOT=Path.of(System.getProperty("java.io.tmpdir"),"exame-perto-benefit-test-"+UUID.randomUUID());
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("storage.private-root",TEST_ROOT::toString);}
    @LocalServerPort int port; @MockBean EmailGateway email; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    @AfterAll static void cleanup() throws Exception{if(Files.exists(TEST_ROOT))try(var paths=Files.walk(TEST_ROOT)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}
    final HttpClient client=HttpClient.newHttpClient();final ConcurrentHashMap<String,String> mail=new ConcurrentHashMap<>();static final String PASS="Senha-sintetica-123";
    @BeforeEach void setup(){when(email.configured()).thenReturn(true);doAnswer(i->{mail.put(i.getArgument(0),i.getArgument(2));return null;}).when(email).send(anyString(),anyString(),anyString());}

    @Test void publicBenefitFlowScopesEvidenceMfaAppealAndStaleVersions() throws Exception {
        Account patient=active("benefit-patient"),family=active("benefit-family"),other=active("benefit-other"),analyst=active("benefit-analyst"),appealAnalyst=active("benefit-appeal");
        var patientResponse=request(patient,"POST","/me/patient","{\"cpf\":\"12345678901\",\"birthDate\":\"1980-01-01\"}");assertThat(patientResponse.statusCode()).isEqualTo(201);UUID patientId=UUID.fromString(body(patientResponse).get("id").toString());
        UUID institution=UUID.randomUUID(),policy=UUID.randomUUID();jdbc.update("INSERT INTO instituicao(id,nome,estado) VALUES (?,?,'HABILITADA')",institution,"Instituição sintética");jdbc.update("INSERT INTO politica_beneficio(id,instituicao_id,codigo,versao,criterios,percentual_maximo,vigencia_inicio,estado) VALUES (?,?,?,1,'{}'::jsonb,100,clock_timestamp(),'ATIVA')",policy,institution,"SYNTHETIC");
        UUID familyId=userId(family.email),patientUser=userId(patient.email),invitation=UUID.randomUUID();jdbc.update("INSERT INTO convite_familiar(id,paciente_id,destinatario_email_busca,token_hash,estado,expira_em,consumido_em,aceito_por,aceito_em) VALUES (?,?,?,?,'CONFIRMADO',clock_timestamp()+interval '2 days',clock_timestamp(),?,clock_timestamp())",invitation,patientId,lookup(family.email),lookup("synthetic-token"),familyId);jdbc.update("INSERT INTO autorizacao_paciente(id,paciente_id,familiar_id,concedida_por,convite_id,confirmada_em,expira_em) VALUES (?,?,?,?,?,clock_timestamp(),clock_timestamp()+interval '2 days')",UUID.randomUUID(),patientId,familyId,patientUser,invitation);UUID grant=jdbc.queryForObject("SELECT id FROM autorizacao_paciente WHERE paciente_id=? AND familiar_id=?",UUID.class,patientId,familyId);jdbc.update("INSERT INTO autorizacao_escopo(autorizacao_id,escopo) VALUES (?, 'BENEFICIOS')",grant);
        String create=json.writeValueAsString(Map.of("patientId",patientId,"policyId",policy,"dimensions",List.of("IDADE","RENDA")));var created=request(patient,"POST","/benefit-requests",create);assertThat(created.statusCode()).as(created.body()).isEqualTo(201);Map<String,Object> requestBody=body(created);UUID requestId=UUID.fromString(requestBody.get("id").toString());assertThat(requestBody.get("status")).isEqualTo("EM_ANALISE");
        assertThat(request(family,"GET","/benefit-requests/"+requestId,null).statusCode()).isEqualTo(200);assertThat(request(other,"GET","/benefit-requests/"+requestId,null).statusCode()).isEqualTo(404);
        jdbc.update("UPDATE autorizacao_paciente SET revogada_em=clock_timestamp() WHERE id=?",grant);assertThat(request(family,"GET","/benefit-requests/"+requestId,null).statusCode()).isEqualTo(404);jdbc.update("UPDATE autorizacao_paciente SET revogada_em=NULL,confirmada_em=clock_timestamp()-interval '2 days',expira_em=clock_timestamp()-interval '1 second' WHERE id=?",grant);assertThat(request(family,"GET","/benefit-requests/"+requestId,null).statusCode()).isEqualTo(404);jdbc.update("UPDATE autorizacao_paciente SET confirmada_em=clock_timestamp(),expira_em=clock_timestamp()+interval '2 days' WHERE id=?",grant);
        byte[] pdf="%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n%%EOF".getBytes();var otherRequest=request(other,"POST","/benefit-requests",json.writeValueAsString(Map.of("patientId",patientId,"policyId",policy,"dimensions",List.of("IDADE"))));assertThat(otherRequest.statusCode()).isEqualTo(404);
        Account otherPatient=active("benefit-other-patient");var op=request(otherPatient,"POST","/me/patient","{\"cpf\":\"98765432109\",\"birthDate\":\"1980-01-01\"}");UUID otherPatientId=UUID.fromString(body(op).get("id").toString());var otherReq=request(otherPatient,"POST","/benefit-requests",json.writeValueAsString(Map.of("patientId",otherPatientId,"policyId",policy,"dimensions",List.of("IDADE"))));UUID otherRequestId=UUID.fromString(body(otherReq).get("id").toString());var otherDoc=upload(otherPatient,otherRequestId,0,"IDADE","other.pdf",pdf);UUID otherDocId=UUID.fromString(((List<Map<String,Object>>)body(otherDoc).get("evidence")).get(0).get("id").toString());
        String reuse=json.writeValueAsString(Map.of("documentId",otherDocId,"purpose","IDADE","requestVersion",0));assertThat(request(patient,"POST","/benefit-requests/"+requestId+"/documents/reuse",reuse).statusCode()).isEqualTo(404);
        var ownDoc=upload(patient,requestId,0,"IDADE","age.pdf",pdf);assertThat(ownDoc.statusCode()).isEqualTo(200);assertThat(body(ownDoc).get("version")).isEqualTo(1);var income=upload(patient,requestId,1,"RENDA","income.pdf",pdf);assertThat(income.statusCode()).isEqualTo(200);assertThat(body(income).get("version")).isEqualTo(2);assertThat(upload(patient,requestId,1,"RENDA","stale.pdf",pdf).statusCode()).isEqualTo(412);
        jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES ((SELECT id FROM usuario WHERE email_busca=?),'ANALISTA_OPERACIONAL')",lookup(patient.email));jdbc.update("INSERT INTO membro_instituicao(instituicao_id,usuario_id,papel) VALUES (?,?, 'ANALISTA_BENEFICIO')",institution,patientUser);mfa(patient);List<Map<String,Object>> selfQueue=list(request(patient,"GET","/analyst/benefit-reviews",null));String reviewId=selfQueue.stream().filter(x->requestId.toString().equals(x.get("requestId"))).findFirst().orElseThrow().get("id").toString();assertThat(request(patient,"POST","/analyst/benefit-reviews/"+reviewId+"/assign",null).statusCode()).isEqualTo(403);
        addAnalyst(institution,analyst);mfa(analyst);List<Map<String,Object>> queue=list(request(analyst,"GET","/analyst/benefit-reviews",null));reviewId=queue.stream().filter(x->requestId.toString().equals(x.get("requestId"))).findFirst().orElseThrow().get("id").toString();assertThat(request(analyst,"POST","/analyst/benefit-reviews/"+reviewId+"/assign",null).statusCode()).isEqualTo(200);
        List<Map<String,Object>> evidence=(List<Map<String,Object>>)body(request(analyst,"GET","/benefit-requests/"+requestId,null)).get("evidence");for(Map<String,Object> doc:evidence)assertThat(request(analyst,"POST","/analyst/benefit-reviews/"+reviewId+"/documents/"+doc.get("id")+"/inspection",null).statusCode()).isEqualTo(200);
        var decided=request(analyst,"POST","/analyst/benefit-reviews/"+reviewId+"/decision",json.writeValueAsString(Map.of("decision","APROVADA","percentage",new java.math.BigDecimal("33.335"),"reason","sintético")));assertThat(decided.statusCode()).isEqualTo(200);Map<String,Object> decidedRequest=body(request(patient,"GET","/benefit-requests/"+requestId,null));assertThat(decidedRequest.get("status")).isEqualTo("DECIDIDA");assertThat(decidedRequest.get("percentage")).isEqualTo(33.34);
        var appeal=request(patient,"POST","/benefit-requests/"+requestId+"/appeal",json.writeValueAsString(Map.of("reason","revisão independente","requestVersion",3)));assertThat(appeal.statusCode()).isEqualTo(200);Map<String,Object> appealBody=body(appeal);assertThat(appealBody.get("status")).isEqualTo("RECURSO");String appealReview=((List<Map<String,Object>>)appealBody.get("reviews")).stream().filter(x->"RECURSO".equals(x.get("type"))).findFirst().orElseThrow().get("id").toString();assertThat(request(analyst,"POST","/analyst/benefit-reviews/"+appealReview+"/assign",null).statusCode()).isEqualTo(403);
        addAnalyst(institution,appealAnalyst);mfa(appealAnalyst);assertThat(request(appealAnalyst,"POST","/analyst/benefit-reviews/"+appealReview+"/assign",null).statusCode()).isEqualTo(200);var rejected=request(appealAnalyst,"POST","/analyst/benefit-reviews/"+appealReview+"/decision",json.writeValueAsString(Map.of("decision","REJEITADA","percentage",0,"reason","recurso sintético")));assertThat(rejected.statusCode()).isEqualTo(200);
        assertThat(request(patient,"POST","/benefit-requests/"+requestId+"/appeal",json.writeValueAsString(Map.of("reason","stale","requestVersion",3))).statusCode()).isEqualTo(412);assertThat(request(patient,"POST","/benefit-requests",json.writeValueAsString(Map.of("patientId",patientId,"policyId",UUID.randomUUID(),"dimensions",List.of("IDADE")))).statusCode()).isEqualTo(422);
    }

    private void addAnalyst(UUID institution,Account a){jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES ((SELECT id FROM usuario WHERE email_busca=?),'ANALISTA_OPERACIONAL') ON CONFLICT DO NOTHING",lookup(a.email));jdbc.update("INSERT INTO membro_instituicao(instituicao_id,usuario_id,papel) VALUES (?,?, 'ANALISTA_BENEFICIO')",institution,userId(a.email));}
    private UUID userId(String email){return jdbc.queryForObject("SELECT id FROM usuario WHERE email_busca=?",UUID.class,lookup(email));}
    private void mfa(Account a)throws Exception{var e=request(a,"POST","/me/mfa/totp/enrollment",json.writeValueAsString(Map.of("password",PASS)));String secret=body(e).get("secret").toString();String code=MfaService.totp(MfaService.decodeBase32(secret),Instant.now().getEpochSecond()/30);assertThat(request(a,"POST","/me/mfa/totp/confirmation",json.writeValueAsString(Map.of("code",code))).statusCode()).isEqualTo(200);}
    private HttpResponse<String> upload(Account a,UUID id,long version,String purpose,String name,byte[] bytes)throws Exception{String boundary="----synthetic"+UUID.randomUUID();ByteArrayOutputStream out=new ByteArrayOutputStream();for(String field:new String[]{"version","purpose"}){String value=field.equals("version")?String.valueOf(version):purpose;out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+field+"\"\r\n\r\n"+value+"\r\n").getBytes());}out.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\""+name+"\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes());out.write(bytes);out.write(("\r\n--"+boundary+"--\r\n").getBytes());return requestBytes(a,"POST","/benefit-requests/"+id+"/documents",out.toByteArray(),"multipart/form-data; boundary="+boundary);}
    private Account active(String prefix)throws Exception{String e=prefix+UUID.randomUUID()+"@example.test";var reg=request(null,"POST","/auth/register",json.writeValueAsString(Map.of("name","Sintético","email",e,"password",PASS)));assertThat(reg.statusCode()).isEqualTo(202);String sent=mail.get(e);assertThat(sent).as("synthetic confirmation message").isNotNull();String token=sent.substring(sent.lastIndexOf(':')+2).trim();assertThat(request(null,"POST","/auth/verification",json.writeValueAsString(Map.of("token",token))).statusCode()).isEqualTo(200);var login=request(null,"POST","/auth/login",json.writeValueAsString(Map.of("email",e,"password",PASS,"client","MOBILE")));assertThat(login.statusCode()).as(login.body()).isEqualTo(200);Map<String,Object> logged=body(login);assertThat(logged.get("accessToken")).as(login.body()).isNotNull();return new Account(e,logged.get("accessToken").toString(),null);}
    private HttpResponse<String> request(Account a,String method,String path,String body)throws Exception{return requestBytes(a,method,path,body==null?null:body.getBytes(),"application/json");}
    private HttpResponse<String> requestBytes(Account a,String method,String path,byte[] body,String contentType)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).header("Content-Type",contentType);if(a!=null)b.header("Authorization","Bearer "+a.access);return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body)).build(),HttpResponse.BodyHandlers.ofString());}
    private Map<String,Object> body(HttpResponse<String> r)throws Exception{return json.readValue(r.body(),new TypeReference<>(){});}
    private List<Map<String,Object>> list(HttpResponse<String> r)throws Exception{assertThat(r.statusCode()).isEqualTo(200);return json.readValue(r.body(),new TypeReference<>(){});}
    private byte[] lookup(String e){try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(java.util.Base64.getDecoder().decode("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="),"HmacSHA256"));return mac.doFinal(e.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception x){throw new RuntimeException(x);}}
    record Account(String email,String access,String refresh){}
}
