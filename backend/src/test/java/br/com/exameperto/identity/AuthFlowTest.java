package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
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
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
    "registration.attempts-per-minute=500","registration.global-attempts-per-minute=2000", "email.queue-delay-ms=100"})
class AuthFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",POSTGRES::getJdbcUrl); r.add("spring.datasource.username",POSTGRES::getUsername); r.add("spring.datasource.password",POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @MockBean EmailGateway email;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    final HttpClient client=HttpClient.newHttpClient();
    final ConcurrentHashMap<String,String> messages=new ConcurrentHashMap<>();
    static final String PASSWORD="Senha-sintetica-123";

    @BeforeEach void setup() {
        when(email.configured()).thenReturn(true);
        doAnswer(i->{ String text=i.getArgument(2); messages.put(i.getArgument(0)+":"+i.getArgument(1),text.substring(text.lastIndexOf(':')+2).trim()); return null; })
            .when(email).send(anyString(),anyString(),anyString());
    }
    @Test void confirmationIsRequiredSingleUseAndCannotPromotePrivileges() throws Exception {
        String address=register(); String token=messages.get(address+":Confirme seu e-mail");
        assertThat(login(address).statusCode()).isEqualTo(401);
        assertThat(post("verification",Map.of("token",token,"role","ADMIN")).statusCode()).isEqualTo(400);
        assertThat(post("verification",Map.of("token",token)).statusCode()).isEqualTo(200);
        assertThat(post("verification",Map.of("token",token)).statusCode()).isEqualTo(401);
        assertThat(login(address).statusCode()).isEqualTo(200);
        assertThat(post("register",Map.of("name","Outro nome","email",address,"password",PASSWORD)).statusCode()).isEqualTo(202);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM desafio_conta WHERE token_hash=decode(?, 'hex')",Long.class,java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes())))).isEqualTo(1);
    }
    @Test void concurrentConfirmationHasOnlyOneWinnerAndExpiredChallengeIsRejected() throws Exception {
        String address=register(); String token=messages.get(address+":Confirme seu e-mail");
        assertThat(race("verification",Map.of("token",token))).containsExactlyInAnyOrder(200,401);
        String another=register(); String expired=messages.get(another+":Confirme seu e-mail");
        jdbc.update("UPDATE desafio_conta SET expira_em=clock_timestamp()-interval '1 second' WHERE consumido_em IS NULL");
        assertThat(post("verification",Map.of("token",expired)).statusCode()).isEqualTo(401);
        assertThat(login(another).statusCode()).isEqualTo(401);
    }
    @Test void refreshReplayRevokesItsSuccessorAndConcurrentRefreshRevokesFamily() throws Exception {
        String address=active(); var first=body(login(address));
        var second=body(post("refresh",Map.of("refreshToken",first.get("refreshToken"))));
        assertThat(post("logout",Map.of(),"Authorization","Bearer "+first.get("accessToken")).statusCode()).isEqualTo(401);
        assertThat(post("refresh",Map.of("refreshToken",first.get("refreshToken"))).statusCode()).isEqualTo(401);
        assertThat(post("refresh",Map.of("refreshToken",second.get("refreshToken"))).statusCode()).isEqualTo(401);
        assertThat(post("logout",Map.of(),"Authorization","Bearer "+second.get("accessToken")).statusCode()).isEqualTo(401);
        var next=body(login(address));
        assertThat(race("refresh",Map.of("refreshToken",next.get("refreshToken")))).containsExactlyInAnyOrder(200,401);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM sessao WHERE familia_id=(SELECT familia_id FROM sessao WHERE access_hash=decode(?,'hex')) AND revogada_em IS NULL",Long.class,hash(next.get("accessToken").toString()))).isZero();
    }
    @Test void expiredAccessCanRefreshButExpiredRefreshCannotAndLogoutRevokes() throws Exception {
        var first=body(login(active())); String refresh=first.get("refreshToken").toString();
        jdbc.update("UPDATE sessao SET access_expira_em=clock_timestamp()-interval '1 second' WHERE refresh_hash=decode(?,'hex')",hash(refresh));
        assertThat(post("logout",Map.of(),"Authorization","Bearer "+first.get("accessToken")).statusCode()).isEqualTo(401);
        var renewed=body(post("refresh",Map.of("refreshToken",refresh)));
        assertThat(post("logout",Map.of(),"Authorization","Bearer "+renewed.get("accessToken")).statusCode()).isEqualTo(204);
        assertThat(post("refresh",Map.of("refreshToken",renewed.get("refreshToken"))).statusCode()).isEqualTo(401);
        var exp=body(login(active()));
        jdbc.update("UPDATE sessao SET expira_em=clock_timestamp()-interval '1 second' WHERE refresh_hash=decode(?,'hex')",hash(exp.get("refreshToken").toString()));
        assertThat(post("refresh",Map.of("refreshToken",exp.get("refreshToken"))).statusCode()).isEqualTo(401);
    }
    @Test void recoveryIsGenericSingleUseAndRevokesAllSessions() throws Exception {
        String address=active(); var first=body(login(address)); var second=body(login(address));
        var known=post("recovery",Map.of("email",address)); var unknown=post("recovery",Map.of("email","absent@example.test"));
        assertThat(known.statusCode()).isEqualTo(202); assertThat(unknown.statusCode()).isEqualTo(202);
        assertThat(body(known).get("status")).isEqualTo(body(unknown).get("status"));
        String token=awaitMessage(address,"Recuperação de acesso");
        assertThat(race("recovery/complete",Map.of("token",token,"newPassword","Nova-senha-sintetica-123"))).containsExactlyInAnyOrder(200,401);
        for (var session:List.of(first,second)) assertThat(post("refresh",Map.of("refreshToken",session.get("refreshToken"))).statusCode()).isEqualTo(401);
        assertThat(login(address).statusCode()).isEqualTo(401);
        assertThat(post("login",Map.of("email",address,"password","Nova-senha-sintetica-123","client","MOBILE")).statusCode()).isEqualTo(200);
    }
    @Test void expiredRecoveryCannotChangePasswordAndResetWinsAgainstConcurrentRefresh() throws Exception {
        String address=active();
        assertThat(post("recovery",Map.of("email",address)).statusCode()).isEqualTo(202);
        String expired=awaitMessage(address,"Recuperação de acesso");
        jdbc.update("UPDATE desafio_conta SET expira_em=clock_timestamp()-interval '1 second',created_at=clock_timestamp()-interval '2 minutes' WHERE tipo='RECUPERACAO'");
        assertThat(post("recovery/complete",Map.of("token",expired,"newPassword","Nova-senha-sintetica-123")).statusCode()).isEqualTo(401);
        var logged=body(login(address));
        jdbc.update("UPDATE outbox SET updated_at=clock_timestamp()-interval '2 minutes' WHERE tipo='RECUPERACAO'");
        messages.remove(address+":Recuperação de acesso");
        assertThat(post("recovery",Map.of("email",address)).statusCode()).isEqualTo(202);
        String token=awaitMessage(address,"Recuperação de acesso");
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var start=new CountDownLatch(1);
            var reset=pool.submit(()->{start.await();return post("recovery/complete",Map.of("token",token,"newPassword","Nova-senha-sintetica-123"));});
            var refresh=pool.submit(()->{start.await();return post("refresh",Map.of("refreshToken",logged.get("refreshToken")));});
            start.countDown(); assertThat(reset.get().statusCode()).isEqualTo(200);
            var rotation=refresh.get(); assertThat(rotation.statusCode()).isIn(200,401);
            if(rotation.statusCode()==200) assertThat(post("refresh",Map.of("refreshToken",body(rotation).get("refreshToken"))).statusCode()).isEqualTo(401);
        }
        assertThat(login(address).statusCode()).isEqualTo(401);
    }
    @Test void resendIsThrottledAndReplacesOldChallenge() throws Exception {
        String address=register(); String old=messages.get(address+":Confirme seu e-mail");
        assertThat(post("verification/resend",Map.of("email",address)).statusCode()).isEqualTo(202);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE tipo='EMAIL'",Long.class)).isZero();
        jdbc.update("UPDATE desafio_conta SET created_at=clock_timestamp()-interval '2 minutes' WHERE consumido_em IS NULL");
        messages.remove(address+":Confirme seu e-mail");
        assertThat(post("verification/resend",Map.of("email",address)).statusCode()).isEqualTo(202);
        String next=awaitMessage(address,"Confirme seu e-mail");
        assertThat(post("verification",Map.of("token",old)).statusCode()).isEqualTo(401);
        assertThat(post("verification",Map.of("token",next)).statusCode()).isEqualTo(200);
    }
    @Test void sendFailureRollsBackRegistrationAndRecoveryRecordsFailureWithoutEnumeration() throws Exception {
        String address=active();
        doThrow(new IllegalStateException("synthetic-provider-failure")).when(email).send(anyString(),anyString(),anyString());
        long before=jdbc.queryForObject("SELECT count(*) FROM usuario",Long.class);
        assertThat(post("register",Map.of("name","Sintético","email","failure@example.test","password",PASSWORD)).statusCode()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM usuario",Long.class)).isEqualTo(before);
        var known=post("recovery",Map.of("email",address)); var unknown=post("recovery",Map.of("email","missing@example.test"));
        assertThat(known.statusCode()).isEqualTo(202); assertThat(unknown.statusCode()).isEqualTo(202);
        for (int i=0;i<100 && jdbc.queryForObject("SELECT count(*) FROM outbox WHERE estado='RECONCILIAR'",Long.class)==0;i++) Thread.sleep(50);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE estado='RECONCILIAR' AND tentativas=1",Long.class)).isPositive();
        when(email.configured()).thenReturn(false);
        assertThat(post("recovery",Map.of("email",address)).statusCode()).isEqualTo(503);
        assertThat(post("recovery",Map.of("email","missing@example.test")).statusCode()).isEqualTo(503);
    }
    @Test void webCookiesRequireActualCsrfAndAllowedOriginAndCannotBecomeMobileCredentials() throws Exception {
        String address=active(); Map<String,Object> request=Map.of("email",address,"password",PASSWORD,"client","WEB");
        assertThat(post("login",request).statusCode()).isEqualTo(403);
        assertThat(post("login",request,"Origin","https://evil.example").statusCode()).isEqualTo(403);
        var logged=post("login",request,"Origin","http://localhost:5173"); assertThat(logged.statusCode()).isEqualTo(200);
        assertThat(body(logged)).doesNotContainKey("refreshToken");
        String setCookie=logged.headers().firstValue("Set-Cookie").orElseThrow();
        assertThat(setCookie).contains("Secure","HttpOnly","SameSite=Lax","Path=/api/v1/auth"); String cookie=setCookie.split(";")[0];
        assertThat(post("refresh",Map.of(),"Cookie",cookie,"Origin","http://localhost:5173","X-CSRF-Token","invented").statusCode()).isEqualTo(403);
        assertThat(post("refresh",Map.of("refreshToken",cookie.substring(8))).statusCode()).isEqualTo(401);
        var csrf=body(post("csrf",Map.of(),"Cookie",cookie,"Origin","http://localhost:5173")).get("token").toString();
        var rotated=post("refresh",Map.of(),"Cookie",cookie,"Origin","http://localhost:5173","X-CSRF-Token",csrf);
        assertThat(rotated.statusCode()).isEqualTo(200);
        String nextCookie=rotated.headers().firstValue("Set-Cookie").orElseThrow().split(";")[0];
        String access=body(rotated).get("accessToken").toString();
        assertThat(post("logout",Map.of(),"Authorization","Bearer "+access).statusCode()).isEqualTo(403);
        String nextCsrf=body(post("csrf",Map.of(),"Cookie",nextCookie,"Origin","http://localhost:5173")).get("token").toString();
        assertThat(post("logout",Map.of(),"Authorization","Bearer "+access,"Cookie",nextCookie,"Origin","http://localhost:5173","X-CSRF-Token",nextCsrf).statusCode()).isEqualTo(204);
        assertThat(post("refresh",Map.of(),"Cookie",nextCookie,"Origin","http://localhost:5173","X-CSRF-Token",nextCsrf).statusCode()).isEqualTo(401);
    }
    @Test void repeatedLoginAttemptsAreLimitedForUnknownAccountsToo() throws Exception {
        String address=UUID.randomUUID()+"@example.test";
        for(int i=0;i<5;i++) assertThat(login(address).statusCode()).isEqualTo(401);
        assertThat(login(address).statusCode()).isEqualTo(429);
    }
    String register() throws Exception {
        String address=UUID.randomUUID()+"@example.test";
        assertThat(post("register",Map.of("name","Conta Sintética","email",address,"password",PASSWORD)).statusCode()).isEqualTo(202); return address;
    }
    String active() throws Exception { String address=register(); assertThat(post("verification",Map.of("token",messages.get(address+":Confirme seu e-mail"))).statusCode()).isEqualTo(200); return address; }
    HttpResponse<String> login(String address) throws Exception { return post("login",Map.of("email",address,"password",PASSWORD,"client","MOBILE")); }
    String awaitMessage(String address,String subject) throws Exception { for(int i=0;i<100;i++){ String token=messages.get(address+":"+subject); if(token!=null)return token; Thread.sleep(50); } throw new AssertionError("Test email boundary not reached"); }
    List<Integer> race(String path,Map<String,Object> body) throws Exception {
        try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
            var start=new CountDownLatch(1); var a=pool.submit(()->{start.await();return post(path,body).statusCode();}); var b=pool.submit(()->{start.await();return post(path,body).statusCode();}); start.countDown(); return List.of(a.get(),b.get());
        }
    }
    Map<String,Object> body(HttpResponse<String> response) throws Exception { return json.readValue(response.body(),new com.fasterxml.jackson.core.type.TypeReference<>(){}); }
    HttpResponse<String> post(String path,Map<String,Object> body,String... headers) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/auth/"+path)).header("Content-Type","application/json");
        if(headers.length>0)request.headers(headers);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
    }
    String hash(String token)throws Exception{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes()));}
}
