package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real browser, HTTP server and disposable PostgreSQL; email boundary is test-only. */
@org.springframework.test.annotation.DirtiesContext
@Testcontainers
@EnabledIfSystemProperty(named="browserTest",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "registration.privacy-approved=true", "registration.allowed-origin=http://127.0.0.1:5187",
    "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
    "registration.attempts-per-minute=100", "registration.global-attempts-per-minute=500","email.queue-delay-ms=100"})
class BrowserFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);}
    @LocalServerPort int port;
    @MockBean EmailGateway email;
    @Test void browserThroughBackendAndPostgresWithoutApiInterception() throws Exception {
        Path mailbox=Files.createTempDirectory("exame-browser-mail-");
        try {
            when(email.configured()).thenAnswer(i->!Files.exists(mailbox.resolve("unavailable")));
            doAnswer(i->{
                String text=i.getArgument(2); String name=i.getArgument(1).toString().contains("Confirme")?"confirmation":"recovery";
                Files.writeString(mailbox.resolve(name),text.substring(text.lastIndexOf(':')+2).trim()); return null;
            }).when(email).send(anyString(),anyString(),anyString());
            var builder=new ProcessBuilder("npm","run","test","--","tests/account-real.spec.ts");
            builder.directory(Path.of("..","web").toFile()); builder.redirectErrorStream(true); builder.redirectOutput(Path.of("target","browser-playwright.log").toFile());
            builder.environment().put("API_TARGET","http://127.0.0.1:"+port);
            builder.environment().put("TEST_MAILBOX",mailbox.toString());
            Process process=builder.start();
            try { assertThat(process.waitFor(120,TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero(); }
            finally { if(process.isAlive())process.destroyForcibly(); }
        } finally {
            try(var files=Files.list(mailbox)){for(Path file:files.toList())Files.deleteIfExists(file);}
            Files.deleteIfExists(mailbox);
        }
    }
}
