package br.com.exameperto.identity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"registration.privacy-approved=false", "registration.attempts-per-minute=50", "registration.global-attempts-per-minute=100"})
class RegistrationPolicyBoundaryTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;

    @Test void missingPrivacyPolicyReturns422WithoutPersistence() throws Exception {
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
            URI.create("http://127.0.0.1:" + port + "/api/v1/auth/register"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"Pessoa de Teste\",\"email\":\"politica@example.test\",\"password\":\"Somente-teste-123\"}"))
            .build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("POLICY_UNDEFINED").doesNotContain("politica@example.test", "Somente-teste");
        for (String table : new String[]{"usuario", "sessao", "desafio_conta", "outbox"}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class)).isZero();
        }
    }
}
