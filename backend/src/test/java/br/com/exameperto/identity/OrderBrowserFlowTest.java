package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
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

@org.springframework.test.annotation.DirtiesContext @Testcontainers @TestMethodOrder(MethodOrderer.OrderAnnotation.class) @EnabledIfSystemProperty(named="browserTest",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"registration.privacy-approved=true","registration.allowed-origin=http://127.0.0.1:5187","security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=","security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=","registration.attempts-per-minute=100","registration.global-attempts-per-minute=500","orders.geocode.enabled=true","orders.geocode.api-key=test-key","orders.require-confirmed-location=true"})
class OrderBrowserFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6"); static final Path ROOT=Path.of(System.getProperty("java.io.tmpdir"),"exame-perto-order-browser-"+UUID.randomUUID());
    static final HttpServer GEOCODER=geocoderServer();
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("storage.private-root",ROOT::toString);r.add("orders.geocode.endpoint",()->"http://127.0.0.1:"+GEOCODER.getAddress().getPort()+"/search");}
    @LocalServerPort int port; @Autowired JdbcTemplate jdbc; @Autowired DataProtector protector; @Autowired ObjectMapper json;
    @AfterAll static void cleanup() throws Exception{GEOCODER.stop(0);if(Files.exists(ROOT))try(var p=Files.walk(ROOT)){p.sorted(java.util.Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(Exception ignored){}});}}
    private static HttpServer geocoderServer(){try{HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);server.createContext("/search",exchange->{byte[] body="{\"features\":[{\"geometry\":{\"coordinates\":[-46.63,-23.55]},\"properties\":{\"label\":\"Rua pública, 10, Centro, São Paulo - SP\",\"id\":\"isolated-public-1\"}}]}".getBytes(java.nio.charset.StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();});server.start();return server;}catch(Exception e){throw new ExceptionInInitializerError(e);}}
    @Test @Order(1) void browserUsesRealOrderApiAndDatabase() throws Exception {
        String email="order-browser-"+UUID.randomUUID()+"@example.test";UUID user=UUID.randomUUID(),patient=UUID.randomUUID();String pass=Passwords.hash("Senha-sintetica-123");jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",user,protector.encrypt(email),protector.lookup(email),pass,protector.encrypt("Paciente navegador"));jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES (?, 'ANALISTA_OPERACIONAL')",user);jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,?,'VERIFICADA')",patient,user,protector.encrypt("12345678901"),protector.lookup("12345678901"),java.time.LocalDate.of(1980,1,1));
        var builder=new ProcessBuilder("npm","run","test","--","tests/orders-real.spec.ts");builder.directory(Path.of("..","web").toFile());builder.redirectErrorStream(true);builder.redirectOutput(Path.of("target","orders-browser-playwright.log").toFile());builder.environment().put("API_TARGET","http://127.0.0.1:"+port);builder.environment().put("VITE_ENABLE_OPERATIONAL_TEST_PANELS","true");builder.environment().put("ORDER_PATIENT_EMAIL",email);builder.environment().put("ORDER_PATIENT_ID",patient.toString());builder.environment().put("ORDER_PATIENT_USER_ID",user.toString());builder.environment().put("ORDER_UNIT_ID",UUID.randomUUID().toString());Process p=builder.start();assertThat(p.waitFor(120,TimeUnit.SECONDS)).isTrue();assertThat(p.exitValue()).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM pedido WHERE paciente_id=? AND estado='EM_VERIFICACAO'",Long.class,patient)).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM autorizacao_retirada a JOIN pedido p ON p.id=a.pedido_id WHERE p.paciente_id=? AND a.estado='PENDENTE'",Long.class,patient)).isZero();
    }
    @Test @Order(2) void browserCreatesAndReconfirmsAddressWithRealSpringAndPostgres() throws Exception {
        String email="address-browser-"+UUID.randomUUID()+"@example.test";UUID user=UUID.randomUUID(),patient=UUID.randomUUID(),unit=UUID.randomUUID(),protocol=UUID.randomUUID();String pass=Passwords.hash("Senha-sintetica-123");
        jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",user,protector.encrypt(email),protector.lookup(email),pass,protector.encrypt("Paciente navegador"));jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES (?, 'ANALISTA_OPERACIONAL')",user);jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,?,'VERIFICADA')",patient,user,protector.encrypt("12345678902"),protector.lookup("12345678902"),java.time.LocalDate.of(1980,1,1));
        jdbc.update("INSERT INTO unidade_retirada(id,nome,cidade,estado,estado_cadastro,criado_por,atualizado_por) VALUES (?,?,'São Paulo','SP','ATIVA',?,?)",unit,"Unidade sintética endereço",user,user);jdbc.update("INSERT INTO aceite_unidade_retirada(id,unidade_id,estado,evidencia_referencia,aceito_por,aceito_em,criado_por) VALUES (?,?, 'ACEITA','isolated-partnership',?,clock_timestamp(),?)",UUID.randomUUID(),unit,user,user);jdbc.update("INSERT INTO protocolo_custodia(id,unidade_id,numero,regras,cobertura_retorno_confirmada,inicio,estado) VALUES (?,?,1,'{}',true,clock_timestamp(),'HABILITADO')",protocol,unit);
        var builder=new ProcessBuilder("npm","run","test","--","tests/address-selection-real.spec.ts");builder.directory(Path.of("..","web").toFile());builder.redirectErrorStream(true);builder.redirectOutput(Path.of("target","address-selection-browser-playwright.log").toFile());builder.environment().put("API_TARGET","http://127.0.0.1:"+port);builder.environment().put("VITE_ENABLE_OPERATIONAL_TEST_PANELS","true");builder.environment().put("ADDRESS_PATIENT_EMAIL",email);builder.environment().put("ADDRESS_PATIENT_ID",patient.toString());builder.environment().put("ADDRESS_UNIT_LABEL","Unidade sintética endereço · São Paulo/SP");builder.environment().put("ADDRESS_CANDIDATES_SCREENSHOT_PATH",Path.of("target","address-selection-candidates-sanitized.png").toAbsolutePath().toString());builder.environment().put("ADDRESS_CONFIRMED_SCREENSHOT_PATH",Path.of("target","address-selection-confirmed-sanitized.png").toAbsolutePath().toString());builder.environment().put("ADDRESS_SCREENSHOT_PATH",Path.of("target","address-selection-confirmed-sanitized.png").toAbsolutePath().toString());Process p=builder.start();assertThat(p.waitFor(120,TimeUnit.SECONDS)).isTrue();assertThat(p.exitValue()).as(Files.readString(Path.of("target","address-selection-browser-playwright.log"))).isZero();
        UUID order=jdbc.queryForObject("SELECT id FROM pedido WHERE paciente_id=? ORDER BY created_at DESC LIMIT 1",UUID.class,patient);assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE id=?",String.class,order)).isEqualTo("EM_VERIFICACAO");Address saved=json.readValue(protector.decrypt(jdbc.queryForObject("SELECT origem_cifrada FROM pedido WHERE id=?",byte[].class,order)),Address.class);assertThat(saved.locationConfirmedAt()).isNotNull();assertThat(saved.locationConfirmationToken()).isNull();assertThat(saved.locationProvider()).isEqualTo("openrouteservice-geocoding");
    }
}
