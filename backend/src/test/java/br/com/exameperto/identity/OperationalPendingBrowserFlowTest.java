package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.mock.mockito.MockBean;
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
    "registration.privacy-approved=true", "registration.allowed-origin=http://127.0.0.1:5187", "security.cookie-secure=false",
    "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=", "registration.attempts-per-minute=1000", "registration.global-attempts-per-minute=5000"
})
class OperationalPendingBrowserFlowTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.6");
    static final int WEB_PORT = findFreePort();
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) { r.add("spring.datasource.url", DB::getJdbcUrl); r.add("spring.datasource.username", DB::getUsername); r.add("spring.datasource.password", DB::getPassword); r.add("registration.allowed-origin", () -> "http://127.0.0.1:" + WEB_PORT); }
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataProtector protector;
    @Autowired ObjectMapper json;
    @MockBean EmailGateway email;

    @Test
    void browserRunsIndependentCancellationAndResolutionJourneys() throws Exception {
        when(email.configured()).thenReturn(true); doAnswer(i -> null).when(email).send(anyString(), anyString(), anyString());
        Account patient = account("pending-patient"); UUID patientId = patient(patient);
        UUID unit = unit(patient.id); Policies policies = policies(unit);
        UUID before = order(patientId, patient.id, null, null, policies, "DISPONIVEL", 2, false, false, false);
        Driver assigned = driver("pending-assigned"); UUID designated = order(patientId, patient.id, assigned, unit, policies, "ACEITA", 3, true, false, false);
        Driver picked = driver("pending-picked"); UUID pickup = order(patientId, patient.id, picked, unit, policies, "RETIRADA", 4, true, true, false);
        Driver returning = driver("pending-return"); UUID returnOrder = order(patientId, patient.id, returning, unit, policies, "OCORRENCIA", 5, true, true, true);
        Driver redelivery = driver("pending-redelivery"); UUID redeliveryOrder = order(patientId, patient.id, redelivery, unit, policies, "OCORRENCIA", 5, true, true, true);
        Account admin = account("pending-admin-return"); Account admin2 = account("pending-admin-redelivery"); jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES (?, 'ANALISTA_OPERACIONAL'), (?, 'ANALISTA_OPERACIONAL')", admin.id, admin2.id); String secret = enroll(admin); String secret2 = enroll(admin2);

        ProcessBuilder b = new ProcessBuilder("npm", "run", "test", "--", "tests/operational-pending-real.spec.ts"); b.directory(Path.of("..").resolve("web").toFile()); b.redirectErrorStream(true); b.redirectOutput(Path.of("target", "operational-pending-playwright.log").toFile());
        b.environment().put("API_TARGET", "http://127.0.0.1:" + port); b.environment().put("VITE_ENABLE_OPERATIONAL_TEST_PANELS", "true");
        b.environment().put("WEB_PORT", Integer.toString(WEB_PORT)); b.environment().put("WEB_BASE_URL", "http://127.0.0.1:" + WEB_PORT);
        b.environment().put("PENDING_PATIENT_EMAIL", patient.email); b.environment().put("PENDING_RETURN_ADMIN_EMAIL", admin.email); b.environment().put("PENDING_REDELIVERY_ADMIN_EMAIL", admin2.email); b.environment().put("PENDING_ADMIN_MFA_SECRET", secret); b.environment().put("PENDING_RETURN_ADMIN_MFA_SECRET", secret); b.environment().put("PENDING_REDELIVERY_ADMIN_MFA_SECRET", secret2); b.environment().put("PENDING_UNIT_ID", unit.toString()); b.environment().put("PENDING_RETURN_ORDER_ID", returnOrder.toString()); b.environment().put("PENDING_REDELIVERY_ORDER_ID", redeliveryOrder.toString()); b.environment().put("PENDING_RETURN_DRIVER_EMAIL", returning.email); b.environment().put("PENDING_REDELIVERY_DRIVER_EMAIL", redelivery.email); b.environment().put("PENDING_SCENARIO", "resolution"); b.environment().put("EXPECTED_CANCEL_FEEDBACK", "cancelado"); b.environment().put("EXPECTED_CANCEL_PHASE", "cancelamento");
        Process p = null;
        try {
            p = b.start();
            assertThat(p.waitFor(180, TimeUnit.SECONDS)).isTrue(); assertThat(p.exitValue()).isZero();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt(); throw ex;
        } finally {
            stopProcessTree(p);
        }
        assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE id=?", String.class, before)).isEqualTo("CANCELADA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM solicitacao_cancelamento WHERE pedido_id=?", Long.class, before)).isZero();
        assertThat(jdbc.queryForObject("SELECT fase FROM solicitacao_cancelamento WHERE pedido_id=?", String.class, designated)).isEqualTo("APOS_DESIGNACAO_ANTES_RETIRADA");
        assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE id=?", String.class, designated)).isEqualTo("OCORRENCIA");
        assertThat(jdbc.queryForObject("SELECT fase FROM solicitacao_cancelamento WHERE pedido_id=?", String.class, pickup)).isEqualTo("APOS_RETIRADA");
        assertThat(jdbc.queryForObject("SELECT encerrada_em IS NULL FROM custodia WHERE pedido_id=?", Boolean.class, pickup)).isTrue();
        assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE id=?", String.class, returnOrder)).isEqualTo("ENCERRADA_COM_OCORRENCIA");
        assertThat(jdbc.queryForObject("SELECT encerrada_em IS NOT NULL FROM custodia WHERE pedido_id=?", Boolean.class, returnOrder)).isTrue();
        assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE id=?", String.class, redeliveryOrder)).isEqualTo("ENTREGUE");
        assertThat(jdbc.queryForObject("SELECT encerrada_em IS NOT NULL FROM custodia WHERE pedido_id=?", Boolean.class, redeliveryOrder)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_custodia WHERE pedido_id=? AND tipo='RESOLUCAO_EXECUTADA'", Long.class, returnOrder)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_custodia WHERE pedido_id=? AND tipo='RETORNO_ENCERRADO'", Long.class, returnOrder)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_custodia WHERE pedido_id=? AND tipo='RESOLUCAO_EXECUTADA'", Long.class, redeliveryOrder)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM auditoria_politica_operacional", Long.class)).isGreaterThanOrEqualTo(0L);
    }

    private static int findFreePort() { try (ServerSocket socket = new ServerSocket()) { socket.setReuseAddress(true); socket.bind(new InetSocketAddress("127.0.0.1", 0)); return socket.getLocalPort(); } catch (Exception ex) { throw new IllegalStateException("Não foi possível reservar porta para o Vite", ex); } }
    private void stopProcessTree(Process process) throws Exception {
        if (process == null) return;
        process.descendants().forEach(h -> h.destroy());
        process.destroy();
        if (!process.waitFor(5, TimeUnit.SECONDS)) {
            process.descendants().forEach(h -> h.destroyForcibly());
            process.destroyForcibly();
            process.waitFor(5, TimeUnit.SECONDS);
        }
    }

    private String enroll(Account a) throws Exception { HttpResponse<String> login = request("POST", "/api/v1/auth/login", Map.of("email", a.email, "password", "Senha-sintetica-123", "client", "MOBILE"), null); String token = json.readTree(login.body()).get("accessToken").asText(); HttpResponse<String> r = request("POST", "/api/v1/me/mfa/totp/enrollment", Map.of("password", "Senha-sintetica-123"), token); String secret = json.readTree(r.body()).get("secret").asText(); long step = java.time.Instant.now().getEpochSecond() / 30 - 1; request("POST", "/api/v1/me/mfa/totp/confirmation", Map.of("code", MfaService.totp(MfaService.decodeBase32(secret), step)), token); return secret; }
    private HttpResponse<String> request(String method, String path, Object body, String token) throws Exception { var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).header("Content-Type", "application/json"); if (token != null) b.header("Authorization", "Bearer " + token); return HttpClient.newHttpClient().send(b.method(method, HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString()); }
    private Account account(String prefix) { UUID id = UUID.randomUUID(); String email = prefix + "-" + id + "@example.test"; jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())", id, protector.encrypt(email), protector.lookup(email), Passwords.hash("Senha-sintetica-123"), protector.encrypt(prefix)); return new Account(id, email); }
    private UUID patient(Account a) { UUID id = UUID.randomUUID(); String cpf = String.format("%011d", Integer.toUnsignedLong(id.hashCode())); jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,DATE '1980-01-01','VERIFICADA')", id, a.id, protector.encrypt(cpf), protector.lookup(cpf)); return id; }
    private UUID unit(UUID actor) { UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO unidade_retirada(id,nome,cidade,estado,estado_cadastro,criado_por,atualizado_por) VALUES (?,'Unidade pendente','Cidade','SP','ATIVA',?,?)", id, actor, actor); jdbc.update("INSERT INTO aceite_unidade_retirada(id,unidade_id,estado,evidencia_referencia,observacao,aceito_por,aceito_em,criado_por) VALUES (?,?,'ACEITA','fixture','Fixture autorizado',?,clock_timestamp(),?)", UUID.randomUUID(), id, actor, actor); return id; }
    private Policies policies(UUID unit) { UUID tariff=UUID.randomUUID(), cancel=UUID.randomUUID(), assignment=UUID.randomUUID(), protocol=UUID.randomUUID(); int n=Math.abs(UUID.randomUUID().hashCode()%900000)+100; jdbc.update("INSERT INTO tarifa(id,numero,formula_codigo,parametros,inicio,estado) VALUES (?,?,'BASE_KM_MINUTO','{}',clock_timestamp(),'ATIVA')",tariff,n); jdbc.update("INSERT INTO politica_cancelamento(id,numero,criterios,inicio,estado) VALUES (?,?,'{}',clock_timestamp(),'ATIVA')",cancel,n+1); jdbc.update("INSERT INTO politica_designacao(id,numero,limite_tarefas_simultaneas,inicio,estado) VALUES (?,?,1,clock_timestamp(),'ATIVA')",assignment,n+2); jdbc.update("INSERT INTO protocolo_custodia(id,unidade_id,numero,regras,cobertura_retorno_confirmada,inicio,estado) VALUES (?,?,1,'{}',true,clock_timestamp(),'HABILITADO')",protocol,unit); return new Policies(tariff,cancel,assignment,protocol,unit); }
    private UUID order(UUID patient, UUID recipient, Driver driver, UUID unit, Policies p, String state, long version, boolean assign, boolean pickup, boolean incident) { UUID order=UUID.randomUUID(),quote=UUID.randomUUID(),acceptance=UUID.randomUUID(),doc=UUID.randomUUID(),auth=UUID.randomUUID(); byte[] address=protector.encrypt("{\"street\":\"Rua pendente\",\"number\":\"10\",\"district\":\"Centro\",\"city\":\"Cidade\",\"state\":\"SP\",\"postalCode\":\"01001000\",\"latitude\":-23.55,\"longitude\":-46.63}"); jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?,'AUTORIZACAO_RETIRADA',?,'12345678901234567890123456789012','application/pdf',100,'QUARENTENA')",doc,recipient,"synthetic/"+doc); jdbc.update("INSERT INTO pedido(id,paciente_id,solicitante_id,origem_cifrada,destino_cifrada,origem_lat,origem_lon,destino_lat,destino_lon,destinatario_id,unidade_id,protocolo_id,estado,version) VALUES (?,?,?,?,?,-23.55,-46.63,-23.54,-46.62,?,?,?,'DISPONIVEL',?)",order,patient,recipient,address,address,recipient,p.unit,p.protocol,version); jdbc.update("INSERT INTO autorizacao_retirada(id,pedido_id,documento_id,estado,criada_por,valida_ate) VALUES (?,?,?,'VERIFICADA',?,clock_timestamp()+interval '1 day')",auth,order,doc,recipient); jdbc.update("UPDATE pedido SET estado=? WHERE id=?",state,order); jdbc.update("INSERT INTO orcamento(id,pedido_id,paciente_id,tarifa_id,rota_provedor,rota_referencia,distancia_m,duracao_s,rota_calculada_em,transito_incluido,politica_snapshot,frete,paciente_valor,subsidio_valor,moeda,expira_em,aceito_em,estado,politica_cancelamento_id,pedido_version) VALUES (?,?,?,?,'controlled','pending',1000,300,clock_timestamp(),false,'{}',38,38,0,'BRL',clock_timestamp()+interval '1 hour',clock_timestamp(),'ACEITO',?,?)",quote,order,patient,p.tariff,p.cancel,version-1); jdbc.update("INSERT INTO aceite_orcamento(id,orcamento_id,pedido_id,paciente_id,aceito_por,pedido_version,orcamento_version,condicoes_snapshot,frete,paciente_valor,subsidio_valor,moeda) VALUES (?,?,?,?,?,? ,0,'{}',38,38,0,'BRL')",acceptance,quote,order,patient,recipient,version-1); if(assign){ jdbc.update("INSERT INTO designacao(id,pedido_id,entregador_id,vinculo_id,orcamento_id,politica_designacao_id,politica_cancelamento_id,pedido_version,limite_tarefas_snapshot,identificacao_snapshot) VALUES (?,?,?,?,?,?,?,? ,1,'{}')",UUID.randomUUID(),order,driver.id,driver.link,quote,p.assignment,p.cancel,version-1); } if(pickup){ UUID custody=UUID.randomUUID(); UUID assignment=jdbc.queryForObject("SELECT id FROM designacao WHERE pedido_id=?",UUID.class,order); UUID evidence=driver.evidence; jdbc.update("INSERT INTO custodia(id,pedido_id,designacao_id,protocolo_id,retirada_em,retirada_evidencia_id) VALUES (?,?,?, ?,clock_timestamp(),?)",custody,order,assignment,p.protocol,evidence); } if(incident){ UUID custody=jdbc.queryForObject("SELECT id FROM custodia WHERE pedido_id=?",UUID.class,order); UUID assignment=jdbc.queryForObject("SELECT id FROM designacao WHERE pedido_id=?",UUID.class,order); jdbc.update("INSERT INTO ocorrencia_entrega(id,pedido_id,custodia_id,designacao_id,aberta_por,tipo,descricao,estado) VALUES (?,?,?,?,?,'OUTRA','Ocorrência sintética','ABERTA')",UUID.randomUUID(),order,custody,assignment,recipient); } return order; }
    private Driver driver(String prefix) { Account a=account(prefix); UUID driver=UUID.randomUUID(), vehicle=UUID.randomUUID(), link=UUID.randomUUID(), photo=UUID.randomUUID(), evidence=UUID.randomUUID(); jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?,'FOTO_OPERACIONAL',?,'12345678901234567890123456789012','image/jpeg',100,'INSPECAO_APROVADA'),(?,?, 'COMPROVANTE',?,'12345678901234567890123456789012','application/pdf',100,'INSPECAO_APROVADA')",photo,a.id,"synthetic/"+photo,evidence,a.id,"synthetic/"+evidence); jdbc.update("INSERT INTO entregador(id,usuario_id,nascimento,estado,foto_aprovada_id) VALUES (?,?,DATE '1990-01-01','APROVADO',?)",driver,a.id,photo); jdbc.update("INSERT INTO veiculo(id,placa,marca,modelo,cor,ano_fabricacao,ano_modelo,estado) VALUES (?,'PEN'||substr(replace(?::text,'-',''),1,4),'M','M','A',2024,2024,'APROVADO')",vehicle,vehicle); jdbc.update("INSERT INTO vinculo_veiculo(id,entregador_id,veiculo_id,tipo,estado,valido_ate,version) VALUES (?,?,?,'PROPRIEDADE','APROVADO',current_date+10,1)",link,driver,vehicle); return new Driver(driver,a.email,link,evidence); }
    record Account(UUID id,String email) {} record Driver(UUID id,String email,UUID link,UUID evidence) {} record Policies(UUID tariff,UUID cancel,UUID assignment,UUID protocol,UUID unit) {}
}
