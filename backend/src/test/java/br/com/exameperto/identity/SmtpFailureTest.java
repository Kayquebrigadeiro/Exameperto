package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
@org.springframework.test.annotation.DirtiesContext
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "registration.privacy-approved=true", "email.enabled=true", "email.from=sender@example.test",
    "spring.mail.host=127.0.0.1", "spring.mail.port=1", "spring.mail.properties.mail.smtp.connectiontimeout=250",
    "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="})
class SmtpFailureTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);}
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Test void actualSmtpConnectionFailureDoesNotReturnSuccessOrPersistPartialAccount() throws Exception {
        var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/auth/register"))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"SMTP Sintético\",\"email\":\"smtp@example.test\",\"password\":\"Senha-sintetica-123\"}")).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(response.body()).contains("INTEGRATION_UNAVAILABLE").doesNotContain("smtp@example.test","Senha-sintetica");
        for(String table:new String[]{"usuario","sessao","desafio_conta","outbox"}) assertThat(jdbc.queryForObject("SELECT count(*) FROM "+table,Long.class)).isZero();
    }
}
