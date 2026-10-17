package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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
@EnabledIfSystemProperty(named="browserTest",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"registration.privacy-approved=true","registration.allowed-origin=http://127.0.0.1:5187","security.cookie-secure=false","security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=","security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=","registration.attempts-per-minute=1000","registration.global-attempts-per-minute=5000"})
class OperationalPolicyBrowserFlowTest {
  @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6");
  @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);}
  @LocalServerPort int port; @Autowired JdbcTemplate jdbc; @Autowired DataProtector protector; @Autowired ObjectMapper json; @MockBean EmailGateway email;
  @Test void browserRunsPolicyLifecycleAndPersistsAudit() throws Exception { when(email.configured()).thenReturn(true); doAnswer(i->null).when(email).send(anyString(),anyString(),anyString()); String emailAddress="policy-lifecycle-"+UUID.randomUUID()+"@example.test"; UUID user=UUID.randomUUID(); jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",user,protector.encrypt(emailAddress),protector.lookup(emailAddress),Passwords.hash("Senha-sintetica-123"),protector.encrypt("policy-lifecycle")); jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES (?, 'ANALISTA_OPERACIONAL')",user); String token=json.readTree(request("POST","/api/v1/auth/login",Map.of("email",emailAddress,"password","Senha-sintetica-123","client","MOBILE"),null).body()).get("accessToken").asText(); String secret=json.readTree(request("POST","/api/v1/me/mfa/totp/enrollment",Map.of("password","Senha-sintetica-123"),token).body()).get("secret").asText(); long step=java.time.Instant.now().getEpochSecond()/30-1; assertThat(request("POST","/api/v1/me/mfa/totp/confirmation",Map.of("code",MfaService.totp(MfaService.decodeBase32(secret),step)),token).statusCode()).isEqualTo(200); ProcessBuilder b=new ProcessBuilder("npm","run","test","--","tests/operational-policy-lifecycle-real.spec.ts"); b.directory(Path.of("..","web").toFile()); b.redirectErrorStream(true); b.redirectOutput(Path.of("target","operational-policy-lifecycle-playwright.log").toFile()); b.environment().put("API_TARGET","http://127.0.0.1:"+port); b.environment().put("VITE_ENABLE_OPERATIONAL_TEST_PANELS","true"); b.environment().put("POLICY_LIFECYCLE_EMAIL",emailAddress); b.environment().put("POLICY_LIFECYCLE_MFA_SECRET",secret); Process p=b.start(); assertThat(p.waitFor(120,TimeUnit.SECONDS)).isTrue(); assertThat(p.exitValue()).isZero(); assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_politica_operacional WHERE ator_id=? AND acao IN ('CRIADA','EDITADA','PUBLICADA','DESATIVADA')",Long.class,user)).isGreaterThanOrEqualTo(4L); }
  private java.net.http.HttpResponse<String> request(String method,String path,Object body,String token)throws Exception {var b=java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://127.0.0.1:"+port+path)).header("Content-Type","application/json");if(token!=null)b.header("Authorization","Bearer "+token);return java.net.http.HttpClient.newHttpClient().send(b.method(method,java.net.http.HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),java.net.http.HttpResponse.BodyHandlers.ofString());}
}
