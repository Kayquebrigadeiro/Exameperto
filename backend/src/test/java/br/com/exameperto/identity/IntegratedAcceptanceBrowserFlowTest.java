package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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

/** Sprint 9 navigation acceptance; domain mutations remain in dedicated browser journeys. */
@org.springframework.test.annotation.DirtiesContext
@Testcontainers
@EnabledIfSystemProperty(named = "browserTest", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "registration.privacy-approved=true", "registration.allowed-origin=http://127.0.0.1:5187",
    "security.cookie-secure=false", "privacy.purge-enabled=true",
    "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
    "registration.attempts-per-minute=1000", "registration.global-attempts-per-minute=5000"})
class IntegratedAcceptanceBrowserFlowTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.6");
    static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "exame-perto-integrated-browser-" + UUID.randomUUID());

    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl); r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword); r.add("storage.private-root", ROOT::toString);
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataProtector protector;
    @MockBean EmailGateway email;
    final String password = "Senha-sintetica-123";

    @AfterAll static void cleanup() throws Exception {
        if (Files.exists(ROOT)) try (var paths = Files.walk(ROOT)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }

    @Test void walksPublicAndAuthenticatedProductWithoutCrossingCapabilities() throws Exception {
        when(email.configured()).thenReturn(true);
        doAnswer(i -> null).when(email).send(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        String emailAddress = account("integrated-acceptance");
        UUID accountId = user(emailAddress);
        UUID institutionBenefit = UUID.randomUUID(), institutionFinance = UUID.randomUUID();
        jdbc.update("INSERT INTO instituicao(id,nome,estado) VALUES (?,?,'HABILITADA'), (?,?,'HABILITADA')", institutionBenefit, "Instituição sintética benefício", institutionFinance, "Instituição sintética financeira");
        jdbc.update("INSERT INTO membro_instituicao(instituicao_id,usuario_id,papel) VALUES (?,?,'ANALISTA_BENEFICIO'), (?,?,'GESTOR_FINANCEIRO')", institutionBenefit, accountId, institutionFinance, accountId);
        jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES (?, 'ANALISTA_OPERACIONAL'), (?, 'GESTOR_FINANCEIRO'), (?, 'OPERADOR_PRIVACIDADE')", accountId, accountId, accountId);
        UUID patient = UUID.randomUUID();
        jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,DATE '1980-01-01','VERIFICADA')", patient, accountId, protector.encrypt("12345678901"), protector.lookup("12345678901"));
        String representedEmail = account("represented-patient");
        UUID representedUser = user(representedEmail), representedPatient = UUID.randomUUID(), invitation = UUID.randomUUID(), grant = UUID.randomUUID();
        jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,DATE '1975-01-01','VERIFICADA')", representedPatient, representedUser, protector.encrypt("22345678901"), protector.lookup("22345678901"));
        jdbc.update("INSERT INTO convite_familiar(id,paciente_id,destinatario_email_busca,token_hash,estado,expira_em,consumido_em,aceito_por,aceito_em,confirmado_em) VALUES (?,?,?,?,'CONFIRMADO',clock_timestamp()+interval '30 days',clock_timestamp(),?,clock_timestamp(),clock_timestamp())", invitation, representedPatient, protector.lookup(emailAddress), protector.tokenHash("synthetic-invitation"), accountId);
        jdbc.update("INSERT INTO convite_familiar_escopo(convite_id,escopo) VALUES (?, 'PEDIDOS'), (?, 'BENEFICIOS')", invitation, invitation);
        jdbc.update("INSERT INTO autorizacao_paciente(id,paciente_id,familiar_id,concedida_por,convite_id,confirmada_em,expira_em) VALUES (?,?,?, ?,?,clock_timestamp(),clock_timestamp()+interval '30 days')", grant, representedPatient, accountId, representedUser, invitation);
        jdbc.update("INSERT INTO autorizacao_escopo(autorizacao_id,escopo) VALUES (?, 'PEDIDOS'), (?, 'BENEFICIOS')", grant, grant);
        UUID deliverer = UUID.randomUUID();
        jdbc.update("INSERT INTO entregador(id,usuario_id,nascimento,estado) VALUES (?,?,DATE '1990-01-01','EM_ANALISE')", deliverer, accountId);

        var builder = new ProcessBuilder("npm", "run", "test", "--", "tests/integrated-acceptance-real.spec.ts");
        builder.directory(Path.of("..", "web").toFile()); builder.redirectErrorStream(true);
        builder.redirectOutput(Path.of("target", "integrated-acceptance-browser-playwright.log").toFile());
        builder.environment().put("API_TARGET", "http://127.0.0.1:" + port);
        builder.environment().put("VITE_ENABLE_OPERATIONAL_TEST_PANELS", "true");
        builder.environment().put("INTEGRATED_ACCEPTANCE_EMAIL", emailAddress);
        Process child = builder.start();
        try { assertThat(child.waitFor(120, TimeUnit.SECONDS)).isTrue(); assertThat(child.exitValue()).isZero(); }
        finally { if (child.isAlive()) child.destroyForcibly(); }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE id=?", Long.class, patient)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM entregador WHERE id=?", Long.class, deliverer)).isEqualTo(1);
    }

    private String account(String prefix) {
        String email = prefix + "-" + UUID.randomUUID() + "@example.test";
        jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())", UUID.randomUUID(), protector.encrypt(email), protector.lookup(email), Passwords.hash(password), protector.encrypt(prefix));
        return email;
    }

    private UUID user(String email) { return jdbc.queryForObject("SELECT id FROM usuario WHERE email_busca=?", UUID.class, protector.lookup(email)); }
}
