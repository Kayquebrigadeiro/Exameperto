package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
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
@EnabledIfSystemProperty(named = "browserTest", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "registration.privacy-approved=true", "registration.allowed-origin=http://127.0.0.1:5187",
    "security.cookie-secure=false", "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
    "registration.attempts-per-minute=1000", "registration.global-attempts-per-minute=5000"})
class OperationalPolicySessionBrowserFlowTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.6");
    static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "exame-perto-policy-session-" + UUID.randomUUID());
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) { r.add("spring.datasource.url", DB::getJdbcUrl); r.add("spring.datasource.username", DB::getUsername); r.add("spring.datasource.password", DB::getPassword); r.add("storage.private-root", ROOT::toString); }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataProtector protector;
    @Autowired ObjectMapper json;
    @MockBean EmailGateway email;
    final HttpClient http = HttpClient.newHttpClient();
    final String password = "Senha-sintetica-123";

    @AfterAll static void cleanup() throws Exception { if (Files.exists(ROOT)) try (var paths = Files.walk(ROOT)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} }); } }

    @Test void browserLoginMfaCreateAndQueryUsesValidSession() throws Exception {
        when(email.configured()).thenReturn(true); doAnswer(i -> null).when(email).send(anyString(), anyString(), anyString());
        String emailAddress = account(); global(emailAddress, "ANALISTA_OPERACIONAL");
        String secret = enrollMfa(emailAddress);
        ProcessBuilder browser = new ProcessBuilder("npm", "run", "test", "--", "tests/operational-policy-session-real.spec.ts");
        browser.directory(Path.of("..", "web").toFile()); browser.redirectErrorStream(true); browser.redirectOutput(Path.of("target", "operational-policy-session-playwright.log").toFile());
        browser.environment().put("API_TARGET", "http://127.0.0.1:" + port); browser.environment().put("VITE_ENABLE_OPERATIONAL_TEST_PANELS", "true"); browser.environment().put("POLICY_SESSION_EMAIL", emailAddress); browser.environment().put("POLICY_SESSION_MFA_SECRET", secret);
        Process child = browser.start();
        try { assertThat(child.waitFor(120, TimeUnit.SECONDS)).isTrue(); assertThat(child.exitValue()).isZero(); }
        finally { if (child.isAlive()) child.destroyForcibly(); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tarifa WHERE escopo='browser-session-policy' AND estado='RASCUNHO'", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_politica_operacional WHERE escopo='browser-session-policy' AND acao='CRIADA'", Long.class)).isEqualTo(1);
    }
    @Test void expiredElevationIsRejectedWithoutChangingSessionAuthorization() throws Exception {
        String email = account(); global(email, "ANALISTA_OPERACIONAL"); String token = login(email); String secret = enrollMfa(email);
        long step = java.time.Instant.now().getEpochSecond() / 30;
        assertThat(request(token, "POST", "/me/mfa/verification", json.writeValueAsString(Map.of("code", MfaService.totp(MfaService.decodeBase32(secret), step)))).statusCode()).isEqualTo(200);
        jdbc.update("UPDATE sessao SET mfa_verificada_ate=clock_timestamp()-interval '1 second' WHERE usuario_id=? AND cliente='MOBILE'", user(email));
        HttpResponse<String> response = request(token, "POST", "/admin/operational-policies/tariffs", tariffBody("expired-elevation-policy"));
        assertThat(response.statusCode()).isEqualTo(403); assertThat(response.body()).contains("MFA_REQUIRED");
    }
    @Test void revokedSessionCannotCreatePolicyEvenWithMfaConfigured() throws Exception {
        String email = account(); global(email, "ANALISTA_OPERACIONAL"); String token = login(email);
        jdbc.update("UPDATE sessao SET revogada_em=clock_timestamp() WHERE usuario_id=? AND cliente='MOBILE'", user(email));
        assertThat(request(token, "POST", "/admin/operational-policies/tariffs", tariffBody("revoked-session-policy")).statusCode()).isEqualTo(401);
    }
    private String account() { String email = "policy-session-" + UUID.randomUUID() + "@example.test"; UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())", id, protector.encrypt(email), protector.lookup(email), Passwords.hash(password), protector.encrypt("policy-session")); return email; }
    private UUID user(String email) { return jdbc.queryForObject("SELECT id FROM usuario WHERE email_busca=?", UUID.class, protector.lookup(email)); }
    private void global(String email, String role) { jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES (?,?)", user(email), role); }
    private String enrollMfa(String email) throws Exception { String token = login(email); String secret = json.readTree(request(token, "POST", "/me/mfa/totp/enrollment", json.writeValueAsString(Map.of("password", password))).body()).get("secret").asText(); long step = java.time.Instant.now().getEpochSecond() / 30 - 1; String code = MfaService.totp(MfaService.decodeBase32(secret), step); assertThat(request(token, "POST", "/me/mfa/totp/confirmation", json.writeValueAsString(Map.of("code", code))).statusCode()).isEqualTo(200); return secret; }
    private String login(String email) throws Exception { return json.readTree(request(null, "POST", "/auth/login", json.writeValueAsString(Map.of("email", email, "password", password, "client", "MOBILE"))).body()).get("accessToken").asText(); }
    private HttpResponse<String> request(String token, String method, String path, String body) throws Exception { var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path)).header("Content-Type", "application/json"); if (token != null) b.header("Authorization", "Bearer " + token); return http.send(b.method(method, HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString()); }
    private String tariffBody(String scope) throws Exception { return json.writeValueAsString(Map.ofEntries(Map.entry("scope", scope), Map.entry("baseAmount", 10), Map.entry("perKmAmount", 2), Map.entry("perMinuteAmount", 0.1), Map.entry("minimumAmount", 5), Map.entry("roundingScale", 2), Map.entry("maximumDistanceMeters", 50000), Map.entry("quoteValiditySeconds", 900), Map.entry("acceptanceDeadlineSeconds", 900), Map.entry("executionDeadlineSeconds", 3600), Map.entry("returnAmount", 5), Map.entry("returnCoverage", "UNIDADE"), Map.entry("startsAt", java.time.Instant.now().toString()))); }
}
