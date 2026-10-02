package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import javax.sql.DataSource;
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
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "registration.privacy-approved=true", "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
    "registration.attempts-per-minute=1000","registration.global-attempts-per-minute=5000","email.queue-delay-ms=50"})
class RepresentationFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl); r.add("spring.datasource.username",POSTGRES::getUsername); r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @MockBean EmailGateway email;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ObjectMapper json;
    final HttpClient client=HttpClient.newHttpClient();
    final ConcurrentHashMap<String,String> messages=new ConcurrentHashMap<>();
    static final String PASSWORD="Senha-sintetica-123";
    static final Pattern INVITE=Pattern.compile("Identificador do convite: ([0-9a-f-]{36}) Código de uso único: ([A-Za-z0-9_-]{43})");

    @BeforeEach void setup() {
        when(email.configured()).thenReturn(true);
        doAnswer(i->{messages.put(i.getArgument(0)+":"+i.getArgument(1),i.getArgument(2));return null;}).when(email).send(anyString(),anyString(),anyString());
    }

    @Test void patientProfileIsPendingOwnerOnlyAndVersioned() throws Exception {
        Account owner=active("owner"); Account other=active("other");
        var created=request(owner,"POST","/me/patient",Map.of("cpf","11111111111","birthDate","1990-01-01"));
        assertThat(created.statusCode()).isEqualTo(201); assertThat(body(created).get("identityStatus")).isEqualTo("PENDENTE");
        UUID patient=UUID.fromString(body(created).get("id").toString());
        assertThat(request(other,"GET","/patients/"+patient,null).statusCode()).isEqualTo(404);
        assertThat(request(owner,"PUT","/me/patient",Map.of("cpf","22222222222","birthDate","1991-02-02"),"If-Match","\"9\"").statusCode()).isEqualTo(412);
        var updated=request(owner,"PUT","/me/patient",Map.of("cpf","22222222222","birthDate","1991-02-02"),"If-Match","\"0\"");
        assertThat(updated.statusCode()).isEqualTo(200); assertThat(updated.headers().firstValue("ETag")).contains("\"1\"");
        assertThat(body(updated).get("identityStatus")).isEqualTo("PENDENTE");
    }

    @Test void invitationIsBoundSingleUseAndGrantRequiresReauthenticationAndExactScopes() throws Exception {
        Account owner=active("patient"); Account family=active("family"); Account stranger=active("stranger"); UUID patient=createPatient(owner,"33333333333");
        Invitation invitation=invite(owner,patient,family.email,"PEDIDOS");
        assertThat(request(stranger,"POST","/family-invitations/"+invitation.id+"/accept",Map.of("token",invitation.token),"If-Match","\"0\"").statusCode()).isEqualTo(404);
        assertThat(request(family,"POST","/family-invitations/"+invitation.id+"/accept",Map.of("token",invitation.token),"If-Match","\"0\"").statusCode()).isEqualTo(200);
        assertThat(request(family,"POST","/family-invitations/"+invitation.id+"/accept",Map.of("token",invitation.token),"If-Match","\"1\"").statusCode()).isEqualTo(409);
        Map<String,Object> grant=Map.of("invitationId",invitation.id.toString(),"scopes",new String[]{"PEDIDOS"},"expiresAt",Instant.now().plusSeconds(3600).toString(),"password",PASSWORD);
        assertThat(request(owner,"POST","/patients/"+patient+"/grants",Map.of("invitationId",invitation.id.toString(),"scopes",new String[]{"PEDIDOS","BENEFICIOS"},"expiresAt",Instant.now().plusSeconds(3600).toString(),"password",PASSWORD)).statusCode()).isEqualTo(409);
        assertThat(request(owner,"POST","/patients/"+patient+"/grants",Map.of("invitationId",invitation.id.toString(),"scopes",new String[]{"PEDIDOS"},"expiresAt",Instant.now().plusSeconds(3600).toString(),"password","senha-incorreta")).statusCode()).isEqualTo(401);
        var confirmed=request(owner,"POST","/patients/"+patient+"/grants",grant);
        assertThat(confirmed.statusCode()).isEqualTo(201); UUID grantId=UUID.fromString(body(confirmed).get("id").toString());
        assertThat(request(family,"GET","/patients/"+patient+"?scope=PEDIDOS",null).statusCode()).isEqualTo(200);
        assertThat(request(family,"GET","/patients/"+patient+"?scope=BENEFICIOS",null).statusCode()).isEqualTo(404);
        assertThat(request(owner,"DELETE","/patients/"+patient+"/grants/"+grantId,null,"If-Match","\"0\"").statusCode()).isEqualTo(204);
        assertThat(request(family,"GET","/patients/"+patient+"?scope=PEDIDOS",null).statusCode()).isEqualTo(404);
        assertThat(items(request(family,"GET","/me/family-authorizations",null))).isZero();
    }

    @Test void expiredAndForwardedInvitationsRemainUnusable() throws Exception {
        Account owner=active("expiry-owner"); Account family=active("expiry-family"); Account other=active("expiry-other"); UUID patient=createPatient(owner,"44444444444");
        Invitation first=invite(owner,patient,family.email,"RECEBIMENTO");
        assertThat(request(other,"POST","/family-invitations/"+first.id+"/accept",Map.of("token",first.token),"If-Match","\"0\"").statusCode()).isEqualTo(404);
        jdbc.update("UPDATE convite_familiar SET expira_em=clock_timestamp()-interval '1 second' WHERE id=?",first.id);
        assertThat(request(family,"POST","/family-invitations/"+first.id+"/accept",Map.of("token",first.token),"If-Match","\"0\"").statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT consumido_em IS NULL FROM convite_familiar WHERE id=?",Boolean.class,first.id)).isTrue();
    }

    @Test void concurrentAcceptAndConfirmationHaveOneWinner() throws Exception {
        Account owner=active("race-owner"); Account family=active("race-family"); UUID patient=createPatient(owner,"55555555555");
        Invitation invitation=invite(owner,patient,family.email,"RASTREAMENTO");
        var accepted=race(()->request(family,"POST","/family-invitations/"+invitation.id+"/accept",Map.of("token",invitation.token),"If-Match","\"0\"").statusCode());
        assertThat(accepted).contains(200).allMatch(code->code==200||code==412);
        Map<String,Object> grant=Map.of("invitationId",invitation.id.toString(),"scopes",new String[]{"RASTREAMENTO"},"expiresAt",Instant.now().plusSeconds(3600).toString(),"password",PASSWORD);
        var confirmed=race(()->request(owner,"POST","/patients/"+patient+"/grants",grant).statusCode());
        assertThat(confirmed).contains(201).allMatch(code->code==201||code==409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM autorizacao_paciente WHERE convite_id=?",Long.class,invitation.id)).isEqualTo(1);
    }

    @Test void expirationIsRecheckedWithAnExistingSession() throws Exception {
        Account owner=active("live-owner"); Account family=active("live-family"); UUID patient=createPatient(owner,"66666666666");
        Invitation invitation=invite(owner,patient,family.email,"BENEFICIOS");
        request(family,"POST","/family-invitations/"+invitation.id+"/accept",Map.of("token",invitation.token),"If-Match","\"0\"");
        var confirmed=request(owner,"POST","/patients/"+patient+"/grants",Map.of("invitationId",invitation.id.toString(),"scopes",new String[]{"BENEFICIOS"},"expiresAt",Instant.now().plusSeconds(3600).toString(),"password",PASSWORD));
        UUID grant=UUID.fromString(body(confirmed).get("id").toString());
        assertThat(request(family,"GET","/patients/"+patient+"?scope=BENEFICIOS",null).statusCode()).isEqualTo(200);
        jdbc.update("UPDATE autorizacao_paciente SET confirmada_em=clock_timestamp()-interval '2 seconds',expira_em=clock_timestamp()-interval '1 second' WHERE id=?",grant);
        assertThat(request(family,"GET","/patients/"+patient+"?scope=BENEFICIOS",null).statusCode()).isEqualTo(404);
        assertThat(items(request(family,"GET","/me/family-authorizations",null))).isZero();
    }

    @Test void unavailableOrFailingChannelLeavesNoInvitation() throws Exception {
        Account owner=active("failure-owner"); Account family=active("failure-family"); UUID patient=createPatient(owner,"77777777777");
        when(email.configured()).thenReturn(false);
        assertThat(request(owner,"POST","/patients/"+patient+"/invitations",Map.of("recipientEmail",family.email,"scopes",new String[]{"PEDIDOS"})).statusCode()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM convite_familiar WHERE paciente_id=?",Long.class,patient)).isZero();
        when(email.configured()).thenReturn(true); doThrow(new IllegalStateException("synthetic-channel-failure")).when(email).send(eq(family.email),eq("Convite privado do Exame Perto"),anyString());
        assertThat(request(owner,"POST","/patients/"+patient+"/invitations",Map.of("recipientEmail",family.email,"scopes",new String[]{"PEDIDOS"})).statusCode()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM convite_familiar WHERE paciente_id=?",Long.class,patient)).isZero();
    }

    @Test void concurrentRevocationHasACommitBoundaryAndThenDeniesTheOpenSession() throws Exception {
        Account owner=active("revoke-owner"); Account family=active("revoke-family"); UUID patient=createPatient(owner,"99999999999");
        Invitation invitation=invite(owner,patient,family.email,"PEDIDOS");
        request(family,"POST","/family-invitations/"+invitation.id+"/accept",Map.of("token",invitation.token),"If-Match","\"0\"");
        var confirmed=request(owner,"POST","/patients/"+patient+"/grants",Map.of("invitationId",invitation.id.toString(),"scopes",new String[]{"PEDIDOS"},"expiresAt",Instant.now().plusSeconds(3600).toString(),"password",PASSWORD));
        UUID grant=UUID.fromString(body(confirmed).get("id").toString());
        UUID familyId=jdbc.queryForObject("SELECT familiar_id FROM autorizacao_paciente WHERE id=?",UUID.class,grant);
        try(var connection=dataSource.getConnection();var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            connection.setAutoCommit(false);
            try(var statement=connection.prepareStatement("""
                SELECT a.id FROM autorizacao_paciente a
                JOIN autorizacao_escopo e ON e.autorizacao_id=a.id
                WHERE a.id=? AND a.familiar_id=?
                  AND a.revogada_em IS NULL AND a.expira_em>clock_timestamp() AND e.escopo='PEDIDOS'
                FOR SHARE OF a
                """)) {
                statement.setObject(1,grant); statement.setObject(2,familyId);
                try(var result=statement.executeQuery()){assertThat(result.next()).isTrue();assertThat(result.getObject(1,UUID.class)).isEqualTo(grant);}
            }
            var started=new CountDownLatch(1);
            var revocation=pool.submit(()->{started.countDown();return request(owner,"DELETE","/patients/"+patient+"/grants/"+grant,null,"If-Match","\"0\"").statusCode();});
            started.await(); Thread.sleep(150);
            assertThat(revocation.isDone()).isFalse();
            connection.commit(); assertThat(revocation.get()).isEqualTo(204);
        }
        assertThat(request(family,"GET","/patients/"+patient+"?scope=PEDIDOS",null).statusCode()).isEqualTo(404);
    }

    private Account active(String prefix) throws Exception {
        String emailAddress=prefix+"-"+UUID.randomUUID()+"@example.test";
        assertThat(publicPost("register",Map.of("name","Conta Sintética","email",emailAddress,"password",PASSWORD)).statusCode()).isEqualTo(202);
        String text=messages.get(emailAddress+":Confirme seu e-mail"); String token=text.substring(text.lastIndexOf(':')+2).trim();
        assertThat(publicPost("verification",Map.of("token",token)).statusCode()).isEqualTo(200);
        var login=publicPost("login",Map.of("email",emailAddress,"password",PASSWORD,"client","MOBILE"));
        return new Account(emailAddress,body(login).get("accessToken").toString());
    }
    private UUID createPatient(Account account,String cpf) throws Exception {
        var response=request(account,"POST","/me/patient",Map.of("cpf",cpf,"birthDate","1990-01-01"));
        assertThat(response.statusCode()).isEqualTo(201); return UUID.fromString(body(response).get("id").toString());
    }
    private Invitation invite(Account owner,UUID patient,String recipient,String scope) throws Exception {
        var response=request(owner,"POST","/patients/"+patient+"/invitations",Map.of("recipientEmail",recipient,"scopes",new String[]{scope}));
        assertThat(response.statusCode()).isEqualTo(201); assertThat(response.body()).doesNotContain("Código de uso único");
        var matcher=INVITE.matcher(messages.get(recipient+":Convite privado do Exame Perto")); assertThat(matcher.find()).isTrue();
        return new Invitation(UUID.fromString(matcher.group(1)),matcher.group(2));
    }
    private HttpResponse<String> publicPost(String path,Object value) throws Exception {
        return send("POST","/auth/"+path,value,null);
    }
    private HttpResponse<String> request(Account account,String method,String path,Object value,String... headers) throws Exception {
        return send(method,path,value,account.access,headers);
    }
    private HttpResponse<String> send(String method,String path,Object value,String access,String... headers) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).header("Content-Type","application/json");
        if(access!=null)builder.header("Authorization","Bearer "+access); if(headers.length>0)builder.headers(headers);
        HttpRequest.BodyPublisher publisher=value==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(value));
        return client.send(builder.method(method,publisher).build(),HttpResponse.BodyHandlers.ofString());
    }
    private Map<String,Object> body(HttpResponse<String> response)throws Exception{return json.readValue(response.body(),new TypeReference<>(){});}
    private int items(HttpResponse<String> response)throws Exception{return ((java.util.List<?>)body(response).get("items")).size();}
    private java.util.List<Integer> race(ThrowingInt action)throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) { var start=new CountDownLatch(1); var a=pool.submit(()->{start.await();return action.get();}); var b=pool.submit(()->{start.await();return action.get();}); start.countDown(); return java.util.List.of(a.get(),b.get()); }
    }
    @FunctionalInterface interface ThrowingInt { int get() throws Exception; }
    record Account(String email,String access) {}
    record Invitation(UUID id,String token) {}
}
