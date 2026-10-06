package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import java.math.BigDecimal;
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

@org.springframework.test.annotation.DirtiesContext @Testcontainers @EnabledIfSystemProperty(named="browserTest",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"registration.privacy-approved=true","registration.allowed-origin=http://127.0.0.1:5187","security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=","security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="})
class AcceptanceBrowserFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");static final Path ROOT=Path.of(System.getProperty("java.io.tmpdir"),"exame-perto-accept-browser-"+UUID.randomUUID());
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("storage.private-root",ROOT::toString);}
    @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired DataProtector protector;@MockBean PaymentProvider payments;
    @AfterAll static void cleanup()throws Exception{if(Files.exists(ROOT))try(var p=Files.walk(ROOT)){p.sorted(java.util.Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(Exception ignored){}});}}
    @Test void browserAcceptsButDoesNotConfirmPayment()throws Exception{
        when(payments.configured()).thenReturn(true);when(payments.providerId()).thenReturn("controlled-browser-test");
        String email="accept-browser-"+UUID.randomUUID()+"@example.test";UUID user=UUID.randomUUID(),patient=UUID.randomUUID(),doc=UUID.randomUUID(),order=UUID.randomUUID(),auth=UUID.randomUUID(),tariff=UUID.randomUUID(),policy=UUID.randomUUID(),quote=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",user,protector.encrypt(email),protector.lookup(email),Passwords.hash("Senha-sintetica-123"),protector.encrypt("Paciente navegador"));jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,?,'VERIFICADA')",patient,user,protector.encrypt("12345678901"),protector.lookup("12345678901"),java.time.LocalDate.of(1980,1,1));jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?, 'AUTORIZACAO_RETIRADA',?,'12345678901234567890123456789012','application/pdf',100,'QUARENTENA')",doc,user,"synthetic/"+doc);
        byte[] address=protector.encrypt("{\"street\":\"Rua sintética\",\"number\":\"10\",\"district\":\"Centro\",\"city\":\"Cidade\",\"state\":\"SP\",\"postalCode\":\"01001000\",\"latitude\":-23.55,\"longitude\":-46.63}");jdbc.update("INSERT INTO pedido(id,paciente_id,solicitante_id,origem_cifrada,destino_cifrada,origem_lat,origem_lon,destino_lat,destino_lon,destinatario_id,unidade_id,estado,version) VALUES (?,?,?,?,?,-23.55,-46.63,-22.90,-43.17,?,?,'AGUARDANDO_ACEITE',1)",order,patient,user,address,address,user,UUID.randomUUID());jdbc.update("INSERT INTO autorizacao_retirada(id,pedido_id,documento_id,estado,criada_por,valida_ate) VALUES (?,?,?,'VERIFICADA',?,clock_timestamp()+interval '1 day')",auth,order,doc,user);jdbc.update("INSERT INTO tarifa(id,numero,formula_codigo,parametros,inicio,estado) VALUES (?,1,'BASE_KM_MINUTO','{}',clock_timestamp(),'ATIVA')",tariff);jdbc.update("INSERT INTO politica_cancelamento(id,numero,criterios,inicio,estado) VALUES (?,1,'{}',clock_timestamp(),'ATIVA')",policy);jdbc.update("INSERT INTO orcamento(id,pedido_id,paciente_id,tarifa_id,rota_provedor,rota_referencia,distancia_m,duracao_s,rota_calculada_em,transito_incluido,politica_snapshot,frete,paciente_valor,subsidio_valor,moeda,expira_em,estado,politica_cancelamento_id,pedido_version) VALUES (?,?,?,?, 'controlled-route','route-browser',12500,1800,clock_timestamp(),false,'{}',38,38,0,'BRL',clock_timestamp()+interval '15 minutes','PROPOSTO',?,1)",quote,order,patient,tariff,policy);
        var builder=new ProcessBuilder("npm","run","test","--","tests/acceptance-real.spec.ts");builder.directory(Path.of("..","web").toFile());builder.redirectErrorStream(true);builder.redirectOutput(Path.of("target","acceptance-browser-playwright.log").toFile());builder.environment().put("API_TARGET","http://127.0.0.1:"+port);builder.environment().put("ACCEPTANCE_EMAIL",email);builder.environment().put("ACCEPTANCE_ORDER_ID",order.toString());Process p=builder.start();assertThat(p.waitFor(120,TimeUnit.SECONDS)).isTrue();assertThat(p.exitValue()).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM aceite_orcamento WHERE orcamento_id=?",Long.class,quote)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT estado FROM operacao_financeira WHERE pedido_id=?",String.class,order)).isEqualTo("PENDENTE");assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE id=?",String.class,order)).isEqualTo("AGUARDANDO_ACEITE");
    }
}
