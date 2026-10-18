package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
    "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="
})
class BenefitCoverageBrowserFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "exame-perto-coverage-browser-" + UUID.randomUUID());
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) { r.add("spring.datasource.url", POSTGRES::getJdbcUrl); r.add("spring.datasource.username", POSTGRES::getUsername); r.add("spring.datasource.password", POSTGRES::getPassword); r.add("storage.private-root", ROOT::toString); }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataProtector protector;
    @Autowired ObjectMapper json;
    @MockBean RouteProvider routes;
    final HttpClient http = HttpClient.newHttpClient();
    static final String PASS = "Senha-sintetica-123";

    @AfterAll static void cleanup() throws Exception { if (Files.exists(ROOT)) try (var walk = Files.walk(ROOT)) { walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) {} }); } }

    @Test void browserSelectsEligibleProgramAndPersistsCoverage() throws Exception {
        when(routes.route(any(), any())).thenReturn(new RouteProvider.RouteResult("controlled-router", "coverage-route", 12500, 1800, false, Instant.now()));
        Account account = account();
        Fixture fixture = fixture(account);

        var unauthorized = request(account.token, "POST", "/orders/" + fixture.insufficientOrder + "/quotes", Map.of("programId", fixture.foreignProgram), "unauthorized-program", "0");
        assertThat(unauthorized.statusCode()).as(unauthorized.body()).isEqualTo(422);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orcamento WHERE pedido_id=?", Long.class, fixture.insufficientOrder)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva_subsidio WHERE programa_id=?", Long.class, fixture.program)).isZero();

        jdbc.update("UPDATE tarifa SET parametros=jsonb_set(parametros,'{maximumDistanceMeters}','10000') WHERE id=?", fixture.tariff);
        var distanceRejected = request(account.token, "POST", "/orders/" + fixture.insufficientOrder + "/quotes", Map.of(), "distance-limit", "0");
        assertThat(distanceRejected.statusCode()).as(distanceRejected.body()).isEqualTo(422);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM orcamento WHERE pedido_id=?", Long.class, fixture.insufficientOrder)).isZero();
        jdbc.update("UPDATE tarifa SET parametros=jsonb_set(parametros,'{maximumDistanceMeters}','50000') WHERE id=?", fixture.tariff);

        jdbc.update("UPDATE tarifa SET parametros=?::jsonb WHERE id=?", "{\"base\":\"1.00\",\"perKm\":\"0.00\",\"perMinute\":\"0.00\",\"minimum\":\"5.00\",\"roundingScale\":2,\"maximumDistanceMeters\":50000,\"validitySeconds\":900,\"acceptanceDeadlineSeconds\":900,\"executionDeadlineSeconds\":3600,\"returnAmount\":\"5.00\",\"returnCoverage\":\"UNIDADE\"}", fixture.tariff);
        var minimumQuote = request(account.token, "POST", "/orders/" + fixture.insufficientOrder + "/quotes", Map.of(), "minimum-floor", "0");
        assertThat(minimumQuote.statusCode()).as(minimumQuote.body()).isEqualTo(201);
        var minimumQuoteId = json.readTree(minimumQuote.body()).get("id").asText();
        assertThat(json.readTree(minimumQuote.body()).get("grossAmount").decimalValue()).isEqualByComparingTo("5.00");
        jdbc.update("DELETE FROM pedido_idempotencia WHERE orcamento_id=?", UUID.fromString(minimumQuoteId));
        jdbc.update("DELETE FROM orcamento WHERE id=?", UUID.fromString(minimumQuoteId));
        jdbc.update("UPDATE pedido SET estado='EM_VERIFICACAO' WHERE id=?", fixture.insufficientOrder);
        jdbc.update("UPDATE tarifa SET parametros=?::jsonb WHERE id=?", "{\"base\":\"10.00\",\"perKm\":\"2.00\",\"perMinute\":\"0.10\",\"minimum\":\"5.00\",\"roundingScale\":2,\"maximumDistanceMeters\":50000,\"validitySeconds\":900,\"acceptanceDeadlineSeconds\":900,\"executionDeadlineSeconds\":3600,\"returnAmount\":\"5.00\",\"returnCoverage\":\"UNIDADE\"}", fixture.tariff);

        ProcessBuilder browser = new ProcessBuilder("npm", "run", "test", "--", "tests/benefit-coverage-real.spec.ts");
        browser.directory(Path.of("..", "web").toFile());
        browser.redirectErrorStream(true).redirectOutput(Path.of("target", "benefit-coverage-browser-playwright.log").toFile());
        browser.environment().put("API_TARGET", "http://127.0.0.1:" + port);
        browser.environment().put("VITE_ENABLE_OPERATIONAL_TEST_PANELS", "true");
        browser.environment().put("COVERAGE_EMAIL", account.email);
        browser.environment().put("COVERAGE_PROGRAM_ID", fixture.program.toString());
        browser.environment().put("COVERAGE_PROGRAM_LABEL", "Programa cobertura");
        Process child = browser.start();
        assertThat(child.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(child.exitValue()).isZero();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva_subsidio WHERE pedido_id=?", Long.class, fixture.happyOrder)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva_subsidio WHERE programa_id=?", Long.class, fixture.program)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT disponivel FROM conta_programa WHERE programa_id=?", BigDecimal.class, fixture.program)).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("SELECT reservado FROM conta_programa WHERE programa_id=?", BigDecimal.class, fixture.program)).isEqualByComparingTo("38.00");
        assertThat(jdbc.queryForObject("SELECT programa_id FROM orcamento WHERE pedido_id=? AND estado='ACEITO'", UUID.class, fixture.happyOrder)).isEqualTo(fixture.program);
        assertThat(jdbc.queryForObject("SELECT beneficio_solicitacao_id FROM orcamento WHERE pedido_id=? AND estado='ACEITO'", UUID.class, fixture.happyOrder)).isEqualTo(fixture.benefit);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva_subsidio WHERE pedido_id=?", Long.class, fixture.insufficientOrder)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aceite_orcamento WHERE pedido_id=?", Long.class, fixture.insufficientOrder)).isZero();
    }

    private Fixture fixture(Account account) {
        UUID institution = UUID.randomUUID(), program = UUID.randomUUID(), foreignInstitution = UUID.randomUUID(), foreignProgram = UUID.randomUUID(), policy = UUID.randomUUID(), benefit = UUID.randomUUID();
        jdbc.update("INSERT INTO instituicao(id,nome,estado) VALUES (?,?,'HABILITADA'),(?,?,'HABILITADA')", institution, "Instituição cobertura", foreignInstitution, "Instituição externa");
        jdbc.update("INSERT INTO programa(id,instituicao_id,codigo,nome,estado) VALUES (?,?,'COV','Programa cobertura','HABILITADO'),(?,?,'OUT','Programa não autorizado','HABILITADO')", program, institution, foreignProgram, foreignInstitution);
        jdbc.update("INSERT INTO politica_beneficio(id,instituicao_id,codigo,versao,criterios,percentual_maximo,vigencia_inicio,estado) VALUES (?,?,'COV',1,'{}',100,clock_timestamp()-interval '1 day','ATIVA')", policy, institution);
        jdbc.update("INSERT INTO solicitacao_beneficio(id,paciente_id,solicitante_id,instituicao_id,politica_id,politica_versao,politica_snapshot,estado,decisao,percentual,motivo_codigo,decisao_valida_ate) VALUES (?,?,?, ?,?,1,'{}','DECIDIDA','APROVADA',100,'TESTE',clock_timestamp()+interval '1 day')", benefit, account.patient, account.user, institution, policy);
        jdbc.update("INSERT INTO conta_programa(programa_id,disponivel) VALUES (?,38.00)", program);
        UUID tariff = UUID.randomUUID(), cancellation = UUID.randomUUID();
        jdbc.update("INSERT INTO tarifa(id,numero,formula_codigo,parametros,inicio,estado) VALUES (?,1,'BASE_KM_MINUTO','{\"base\":\"10.00\",\"perKm\":\"2.00\",\"perMinute\":\"0.10\",\"minimum\":\"5.00\",\"roundingScale\":2,\"maximumDistanceMeters\":50000,\"validitySeconds\":900,\"acceptanceDeadlineSeconds\":900,\"executionDeadlineSeconds\":3600,\"returnAmount\":\"5.00\",\"returnCoverage\":\"UNIDADE\"}',clock_timestamp(),'ATIVA')", tariff);
        jdbc.update("INSERT INTO politica_cancelamento(id,numero,criterios,inicio,estado) VALUES (?,1,'{}',clock_timestamp(),'ATIVA')", cancellation);
        UUID happy = order(account, tariff, cancellation), insufficient = order(account, tariff, cancellation);
        jdbc.update("UPDATE pedido SET created_at=clock_timestamp()+interval '1 second' WHERE id=?", happy);
        return new Fixture(account, tariff, program, foreignProgram, benefit, happy, insufficient);
    }

    private UUID order(Account account, UUID tariff, UUID cancellation) {
        UUID document = UUID.randomUUID(), order = UUID.randomUUID(), authorization = UUID.randomUUID();
        jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?,'AUTORIZACAO_RETIRADA',?,'12345678901234567890123456789012','application/pdf',100,'QUARENTENA')", document, account.user, "synthetic/" + document);
        byte[] address = protector.encrypt("{\"street\":\"Rua sintética\",\"number\":\"10\",\"district\":\"Centro\",\"city\":\"Cidade\",\"state\":\"SP\",\"postalCode\":\"01001000\",\"latitude\":-23.55,\"longitude\":-46.63}");
        jdbc.update("INSERT INTO pedido(id,paciente_id,solicitante_id,origem_cifrada,destino_cifrada,origem_lat,origem_lon,destino_lat,destino_lon,destinatario_id,unidade_id,estado,version) VALUES (?,?,?,?,?,-23.55,-46.63,-22.90,-43.17,?,?,'EM_VERIFICACAO',0)", order, account.patient, account.user, address, address, account.user, UUID.randomUUID());
        jdbc.update("INSERT INTO autorizacao_retirada(id,pedido_id,documento_id,estado,criada_por,valida_ate) VALUES (?,?,?,'VERIFICADA',?,clock_timestamp()+interval '1 day')", authorization, order, document, account.user);
        return order;
    }

    private Account account() throws Exception {
        UUID user = UUID.randomUUID(), patient = UUID.randomUUID(); String email = "coverage-browser-" + user + "@example.test";
        jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())", user, protector.encrypt(email), protector.lookup(email), Passwords.hash(PASS), protector.encrypt("Paciente cobertura"));
        jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,DATE '1980-01-01','VERIFICADA')", patient, user, protector.encrypt("12345678901"), protector.lookup("12345678901"));
        var response = request(null, "POST", "/auth/login", Map.of("email", email, "password", PASS, "client", "MOBILE"), null, null);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        return new Account(user, patient, email, json.readTree(response.body()).get("accessToken").asText());
    }

    private HttpResponse<String> request(String token, String method, String path, Object body, String idem, String version) throws Exception { var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path)).header("Content-Type", "application/json"); if (token != null) builder.header("Authorization", "Bearer " + token); if (idem != null) builder.header("Idempotency-Key", idem); if (version != null) builder.header("If-Match", version); String payload = body == null ? null : json.writeValueAsString(body); return http.send(builder.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString()); }
    record Account(UUID user, UUID patient, String email, String token) {}
    record Fixture(Account account, UUID tariff, UUID program, UUID foreignProgram, UUID benefit, UUID happyOrder, UUID insufficientOrder) {}
}
