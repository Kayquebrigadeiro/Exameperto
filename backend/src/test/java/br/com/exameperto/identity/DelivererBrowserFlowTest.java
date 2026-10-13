package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class DelivererBrowserFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "exame-perto-deliverer-browser-" + UUID.randomUUID());
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("storage.private-root", ROOT::toString);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataProtector protector;

    @AfterAll static void cleanup() throws Exception {
        if (Files.exists(ROOT)) try (var paths = Files.walk(ROOT)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) {} });
        }
    }

    @Test void browserCompletesDelivererAndVehicleTrackingWithoutApproval() throws Exception {
        UUID user = UUID.randomUUID();
        String email = "deliverer-browser-" + user + "@example.test";
        jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",
            user, protector.encrypt(email), protector.lookup(email), Passwords.hash("Senha-sintetica-123"), protector.encrypt("Entregador navegador"));

        ProcessBuilder builder = new ProcessBuilder("npm", "run", "test", "--", "tests/deliverer-real.spec.ts");
        builder.directory(Path.of("..", "web").toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(Path.of("target", "deliverer-browser-playwright.log").toFile());
        builder.environment().put("API_TARGET", "http://127.0.0.1:" + port);
        builder.environment().put("DELIVERER_EMAIL", email);
        Process process = builder.start();
        try {
            assertThat(process.waitFor(120, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }

        assertThat(jdbc.queryForObject("SELECT estado FROM entregador WHERE usuario_id=?", String.class, user)).isEqualTo("EM_ANALISE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documento WHERE proprietario_id=? AND categoria='HABILITACAO' AND estado='QUARENTENA'", Long.class, user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vinculo_veiculo vv JOIN entregador e ON e.id=vv.entregador_id WHERE e.usuario_id=? AND vv.estado='EM_ANALISE'", Long.class, user)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vinculo_documento vd JOIN vinculo_veiculo vv ON vv.id=vd.vinculo_id JOIN entregador e ON e.id=vv.entregador_id WHERE e.usuario_id=? AND vd.finalidade='CRLV'", Long.class, user)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM vinculo_documento vd JOIN vinculo_veiculo vv ON vv.id=vd.vinculo_id JOIN entregador e ON e.id=vv.entregador_id WHERE e.usuario_id=? AND vd.finalidade='CRLV' AND vd.substituido_em IS NULL", Long.class, user)).isEqualTo(1);
    }
}
