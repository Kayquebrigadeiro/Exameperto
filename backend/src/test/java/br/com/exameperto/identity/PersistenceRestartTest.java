package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import br.com.exameperto.ExamePertoApplication;
import java.net.URI;
import java.net.http.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@Testcontainers
class PersistenceRestartTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    static final AtomicReference<String> TOKEN=new AtomicReference<>();
    @TestConfiguration static class TestMail {
        @Bean @Primary EmailGateway testGateway() { return new EmailGateway() {
            public boolean configured(){return true;}
            public void send(String recipient,String subject,String text){TOKEN.set(text.substring(text.lastIndexOf(':')+2).trim());}
        }; }
    }
    ServletWebServerApplicationContext start() {
        return (ServletWebServerApplicationContext)new SpringApplicationBuilder(ExamePertoApplication.class,TestMail.class).run(
            "--server.port=0","--spring.datasource.url="+POSTGRES.getJdbcUrl(),"--spring.datasource.username="+POSTGRES.getUsername(),"--spring.datasource.password="+POSTGRES.getPassword(),
            "--registration.privacy-approved=true","--security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
            "--security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=","--security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=");
    }
    @Test void confirmedAccountSurvivesFullApplicationRestart() throws Exception {
        try(var first=start()) {
            assertThat(post(first,"register","{\"name\":\"Persistência Sintética\",\"email\":\"restart@example.test\",\"password\":\"Senha-sintetica-123\"}")).isEqualTo(202);
            assertThat(post(first,"verification","{\"token\":\""+TOKEN.get()+"\"}")).isEqualTo(200);
        }
        try(var second=start()) {
            assertThat(post(second,"login","{\"email\":\"restart@example.test\",\"password\":\"Senha-sintetica-123\",\"client\":\"MOBILE\"}")).isEqualTo(200);
        }
    }
    int post(ServletWebServerApplicationContext context,String path,String body) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+context.getWebServer().getPort()+"/api/v1/auth/"+path))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.discarding()).statusCode();
    }
}
