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

@org.springframework.test.annotation.DirtiesContext @Testcontainers @EnabledIfSystemProperty(named="browserTest",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"registration.privacy-approved=true","registration.allowed-origin=http://127.0.0.1:5187","security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=","security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=","registration.attempts-per-minute=100","registration.global-attempts-per-minute=500"})
class OrderBrowserFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6"); static final Path ROOT=Path.of(System.getProperty("java.io.tmpdir"),"exame-perto-order-browser-"+UUID.randomUUID());
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("storage.private-root",ROOT::toString);}
    @LocalServerPort int port; @Autowired JdbcTemplate jdbc; @Autowired DataProtector protector;
    @AfterAll static void cleanup() throws Exception{if(Files.exists(ROOT))try(var p=Files.walk(ROOT)){p.sorted(java.util.Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(Exception ignored){}});}}
    @Test void browserUsesRealOrderApiAndDatabase() throws Exception {
        String email="order-browser-"+UUID.randomUUID()+"@example.test";UUID user=UUID.randomUUID(),patient=UUID.randomUUID();String pass=Passwords.hash("Senha-sintetica-123");jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",user,protector.encrypt(email),protector.lookup(email),pass,protector.encrypt("Paciente navegador"));jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,?,'VERIFICADA')",patient,user,protector.encrypt("12345678901"),protector.lookup("12345678901"),java.time.LocalDate.of(1980,1,1));
        var builder=new ProcessBuilder("npm","run","test","--","tests/orders-real.spec.ts");builder.directory(Path.of("..","web").toFile());builder.redirectErrorStream(true);builder.redirectOutput(Path.of("target","orders-browser-playwright.log").toFile());builder.environment().put("API_TARGET","http://127.0.0.1:"+port);builder.environment().put("ORDER_PATIENT_EMAIL",email);builder.environment().put("ORDER_PATIENT_ID",patient.toString());builder.environment().put("ORDER_PATIENT_USER_ID",user.toString());builder.environment().put("ORDER_UNIT_ID",UUID.randomUUID().toString());Process p=builder.start();assertThat(p.waitFor(120,TimeUnit.SECONDS)).isTrue();assertThat(p.exitValue()).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM pedido WHERE paciente_id=? AND estado='EM_VERIFICACAO'",Long.class,patient)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM autorizacao_retirada a JOIN pedido p ON p.id=a.pedido_id WHERE p.paciente_id=? AND a.estado='PENDENTE'",Long.class,patient)).isEqualTo(1);
    }
}
