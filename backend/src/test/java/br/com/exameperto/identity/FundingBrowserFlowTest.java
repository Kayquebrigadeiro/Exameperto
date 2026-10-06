package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
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

/** Chromium uses the real Vite page and real HTTP API; no API interception or seed reaches application runtime. */
@org.springframework.test.annotation.DirtiesContext
@Testcontainers
@EnabledIfSystemProperty(named="browserTest",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "registration.privacy-approved=true", "registration.allowed-origin=http://127.0.0.1:5187",
    "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
    "registration.attempts-per-minute=100", "registration.global-attempts-per-minute=500", "email.queue-delay-ms=100"})
class FundingBrowserFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    static final Path TEST_ROOT=Path.of(System.getProperty("java.io.tmpdir"),"exame-perto-funding-browser-"+UUID.randomUUID());
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("storage.private-root",TEST_ROOT::toString);}
    @LocalServerPort int port; @MockBean EmailGateway email; @Autowired JdbcTemplate jdbc; @Autowired DataProtector protector;
    @AfterAll static void cleanup() throws Exception{if(Files.exists(TEST_ROOT))try(var paths=Files.walk(TEST_ROOT)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(p->{try{Files.deleteIfExists(p);}catch(Exception ignored){}});}}

    @Test void chromiumFundingPanelUsesRealApiAndDatabase() throws Exception {
        String registrar="funding-browser-registrar@example.test", reviewer="funding-browser-reviewer@example.test", password="Senha-sintetica-123"; UUID institution=UUID.randomUUID(), program=UUID.randomUUID();
        account(registrar); account(reviewer); jdbc.update("INSERT INTO instituicao(id,nome,estado) VALUES (?,?,'HABILITADA')",institution,"Instituição navegador sintética"); jdbc.update("INSERT INTO programa(id,instituicao_id,codigo,nome,estado) VALUES (?,?,?,'Programa navegador sintético','HABILITADO')",program,institution,"BROWSER-FUND");
        grant(institution,registrar); grant(institution,reviewer); global(registrar); global(reviewer);
        Path mailbox=Files.createTempDirectory("exame-funding-browser-mail-");
        try {
            when(email.configured()).thenReturn(true); doAnswer(i->null).when(email).send(anyString(),anyString(),anyString());
            var builder=new ProcessBuilder("npm","run","test","--","tests/funding-real.spec.ts"); builder.directory(Path.of("..","web").toFile()); builder.redirectErrorStream(true); builder.redirectOutput(Path.of("target","funding-browser-playwright.log").toFile());
            builder.environment().put("API_TARGET","http://127.0.0.1:"+port); builder.environment().put("FUNDING_PROGRAM_ID",program.toString()); builder.environment().put("FUNDING_REGISTRAR_EMAIL",registrar); builder.environment().put("FUNDING_REVIEWER_EMAIL",reviewer);
            Process process=builder.start(); try{assertThat(process.waitFor(120,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();}finally{if(process.isAlive())process.destroyForcibly();}
            assertThat(jdbc.queryForObject("SELECT count(*) FROM aporte WHERE programa_id=? AND estado='CONFIRMADO'",Long.class,program)).isEqualTo(1); assertThat(jdbc.queryForObject("SELECT count(*) FROM lancamento_aporte WHERE programa_id=?",Long.class,program)).isEqualTo(1);
        } finally {try(var files=Files.list(mailbox)){for(Path f:files.toList())Files.deleteIfExists(f);}Files.deleteIfExists(mailbox);}
    }
    private void account(String email){UUID id=UUID.randomUUID(); jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",id,protector.encrypt(email),protector.lookup(email),Passwords.hash("Senha-sintetica-123"),protector.encrypt("Conta navegador sintética"));}
    private UUID user(String email){return jdbc.queryForObject("SELECT id FROM usuario WHERE email_busca=?",UUID.class,protector.lookup(email));}
    private void grant(UUID institution,String email){jdbc.update("INSERT INTO membro_instituicao(instituicao_id,usuario_id,papel) VALUES (?,?,'GESTOR_FINANCEIRO')",institution,user(email));}
    private void global(String email){jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES (?, 'GESTOR_FINANCEIRO')",user(email));}
}
