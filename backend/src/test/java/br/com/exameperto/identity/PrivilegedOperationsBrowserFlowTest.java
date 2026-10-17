package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Map;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Browser coverage for the three privileged sprint-6 areas. Fixtures are test-only SQL; accepted actions use the page. */
@org.springframework.test.annotation.DirtiesContext
@Testcontainers
@EnabledIfSystemProperty(named = "browserTest", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "registration.privacy-approved=true", "registration.allowed-origin=http://127.0.0.1:5187",
    "security.cookie-secure=false", "privacy.purge-enabled=true",
    "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
    "registration.attempts-per-minute=1000", "registration.global-attempts-per-minute=5000"})
class PrivilegedOperationsBrowserFlowTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:17.6");
    static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "exame-perto-privileged-browser-" + UUID.randomUUID());
    static final int CLAM_PORT = findFreePort();
    @DynamicPropertySource static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl); r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword); r.add("storage.private-root", ROOT::toString);
        r.add("antimalware.enabled", () -> "true");
        r.add("antimalware.host", () -> "127.0.0.1");
        r.add("antimalware.port", () -> Integer.toString(CLAM_PORT));
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataProtector protector;
    @Autowired PrivateObjectStore objects;
    @Autowired ObjectMapper json;
    @MockBean EmailGateway email;
    final HttpClient http = HttpClient.newHttpClient();
    final String password = "Senha-sintetica-123";

    @AfterAll static void cleanup() throws Exception {
        if (Files.exists(ROOT)) try (var paths = Files.walk(ROOT)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }

    @Test void chromiumExecutesPrivilegedJourneysAndKeepsNegativeCasesBlocked() throws Exception {
        when(email.configured()).thenReturn(true); doAnswer(i -> null).when(email).send(anyString(), anyString(), anyString());
        String operational = account("privileged-operational");
        String self = account("privileged-self");
        String noMfa = account("privileged-no-mfa");
        String benefit = account("privileged-benefit");
        String benefitSubject = account("privileged-benefit-subject");
        String privacy = account("privileged-privacy");
        String privacySubject = account("privileged-privacy-subject");
        String noRole = account("privileged-no-role");
        String operationalSubject = account("privileged-operational-subject");

        UUID operationalDeliverer = operationalFixture(operationalSubject, "operational-document");
        UUID selfDeliverer = operationalFixture(self, "self-document");
        operationalFixture(noMfa, "no-mfa-document");
        benefitFixture(benefit, benefitSubject);
        privacyFixture(privacy, privacySubject);
        UUID privacySubjectId = user(privacySubject);
        global(operational, "ANALISTA_OPERACIONAL"); global(self, "ANALISTA_OPERACIONAL"); global(noMfa, "ANALISTA_OPERACIONAL");
        member(benefit, "ANALISTA_BENEFICIO"); global(privacy, "OPERADOR_PRIVACIDADE");
        String operationalSecret = enrollMfa(operational); String selfSecret = enrollMfa(self); String benefitSecret = enrollMfa(benefit); String privacySecret = enrollMfa(privacy);

        assertThat(get(noRole, "/analyst/reviews").statusCode()).isEqualTo(403);
        assertThat(get(noMfa, "/analyst/reviews").statusCode()).isEqualTo(200);
        assertThat(postToken(login(noMfa), "/analyst/reviews/" + reviewIdFor(noMfa) + "/assign", null).statusCode()).isEqualTo(403);

        ProcessBuilder builder = new ProcessBuilder("npm", "run", "test", "--", "tests/privileged-real.spec.ts");
        builder.directory(Path.of("..", "web").toFile()); builder.redirectErrorStream(true);
        builder.redirectOutput(Path.of("target", "privileged-browser-playwright.log").toFile());
        builder.environment().put("API_TARGET", "http://127.0.0.1:" + port);
        builder.environment().put("VITE_ENABLE_OPERATIONAL_TEST_PANELS", "true");
        builder.environment().put("OPERATIONAL_ANALYST_EMAIL", operational);
        builder.environment().put("OPERATIONAL_SELF_EMAIL", self);
        builder.environment().put("OPERATIONAL_NO_MFA_EMAIL", noMfa);
        builder.environment().put("OPERATIONAL_DELIVERER_ID", operationalDeliverer.toString());
        builder.environment().put("SELF_DELIVERER_ID", selfDeliverer.toString());
        builder.environment().put("OPERATIONAL_MFA_SECRET", operationalSecret);
        builder.environment().put("SELF_MFA_SECRET", selfSecret);
        builder.environment().put("BENEFIT_MFA_SECRET", benefitSecret);
        builder.environment().put("PRIVACY_MFA_SECRET", privacySecret);
        builder.environment().put("BENEFIT_ANALYST_EMAIL", benefit);
        builder.environment().put("PRIVACY_OPERATOR_EMAIL", privacy);
        try (ClamAvProcess clam = ClamAvProcess.start(CLAM_PORT)) {
            Process child = builder.start();
            try { assertThat(child.waitFor(120, TimeUnit.SECONDS)).isTrue(); assertThat(child.exitValue()).isZero(); }
            finally { if (child.isAlive()) child.destroyForcibly(); }
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM revisao_entregador WHERE analista_id=(SELECT id FROM usuario WHERE email_busca=?) AND estado='ATRIBUIDA'", Long.class, lookup(operational))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT d.estado FROM documento d JOIN entregador_documento ed ON ed.documento_id=d.id WHERE ed.entregador_id=?", String.class, operationalDeliverer)).isEqualTo("INSPECAO_APROVADA");
        assertThat(jdbc.queryForObject("SELECT d.antimalware_estado FROM documento d JOIN entregador_documento ed ON ed.documento_id=d.id WHERE ed.entregador_id=?", String.class, operationalDeliverer)).isEqualTo("LIMPO");
        assertThat(jdbc.queryForObject("SELECT estado FROM solicitacao_beneficio WHERE solicitante_id=(SELECT id FROM usuario WHERE email_busca=?)", String.class, lookup(benefitSubject))).isEqualTo("DECIDIDA");
        assertThat(jdbc.queryForObject("SELECT estado FROM usuario WHERE id=?", String.class, privacySubjectId)).isEqualTo("ENCERRADO");
    }

    private UUID operationalFixture(String email, String key) {
        UUID user = user(email), deliverer = UUID.randomUUID(), document = UUID.randomUUID();
        byte[] content = ("%PDF-1.4\n" + key + "\n%%EOF").getBytes();
        String object = objects.put(document, new MockMultipartFile("file", key + ".pdf", "application/pdf", content));
        jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?, 'HABILITACAO',?,?,'application/pdf',?,'QUARENTENA')", document, user, object, hash(content), content.length);
        jdbc.update("INSERT INTO entregador(id,usuario_id,nascimento,estado) VALUES (?,?,DATE '1990-01-01','EM_ANALISE')", deliverer, user);
        jdbc.update("INSERT INTO entregador_documento(entregador_id,documento_id,finalidade) VALUES (?,?,'HABILITACAO')", deliverer, document);
        jdbc.update("INSERT INTO revisao_entregador(id,entregador_id,documento_id,estado) VALUES (?,?,?,'PENDENTE')", UUID.randomUUID(), deliverer, document);
        return deliverer;
    }
    private void benefitFixture(String analystEmail, String subjectEmail) {
        UUID user = user(analystEmail), subject = user(subjectEmail), institution = UUID.randomUUID(), policy = UUID.randomUUID(), patient = UUID.randomUUID(), request = UUID.randomUUID(), document = UUID.randomUUID();
        jdbc.update("INSERT INTO instituicao(id,nome,estado) VALUES (?,?,'HABILITADA')", institution, "Instituição sintética sprint 6");
        jdbc.update("INSERT INTO politica_beneficio(id,instituicao_id,codigo,versao,criterios,percentual_maximo,vigencia_inicio,estado) VALUES (?,?,?,1,'{}'::jsonb,100,clock_timestamp(),'ATIVA')", policy, institution, "S6");
        jdbc.update("INSERT INTO membro_instituicao(instituicao_id,usuario_id,papel) VALUES (?,?,'ANALISTA_BENEFICIO')", institution, user);
        jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,DATE '1980-01-01','VERIFICADA')", patient, subject, protector.encrypt("12345678901"), protector.lookup("12345678901"));
        jdbc.update("INSERT INTO solicitacao_beneficio(id,paciente_id,solicitante_id,instituicao_id,politica_id,politica_versao,politica_snapshot,estado) VALUES (?,?,?,?,?,1,'{}'::jsonb,'EM_ANALISE')", request, patient, subject, institution, policy);
        jdbc.update("INSERT INTO solicitacao_dimensao(solicitacao_id,dimensao) VALUES (?,'IDADE')", request);
        byte[] content = "%PDF-1.4\nidade\n%%EOF".getBytes();
        String object = objects.put(document, new MockMultipartFile("file", "idade.pdf", "application/pdf", content));
        jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?, 'IDADE',?,?,'application/pdf',?,'QUARENTENA')", document, subject, object, hash(content), content.length);
        jdbc.update("INSERT INTO beneficio_evidencia(solicitacao_id,documento_id,finalidade) VALUES (?,?,'IDADE')", request, document);
        jdbc.update("INSERT INTO revisao_beneficio(id,solicitacao_id,tipo,solicitacao_version,estado) VALUES (?,?, 'INICIAL',0,'PENDENTE')", UUID.randomUUID(), request);
    }
    private void privacyFixture(String operatorEmail, String subjectEmail) {
        UUID operator = user(operatorEmail), subject = user(subjectEmail), request = UUID.randomUUID();
        jdbc.update("INSERT INTO politica_retencao(id,categoria,numero,finalidade_codigo,gatilho,prazo_dias,responsavel_id,base_validada,verificacao_descarte,validada_em) VALUES (?,'CONTA',1,'ATENDER_EXCLUSAO_TITULAR','SOLICITACAO_RESPONDIDA',0,?,true,true,clock_timestamp())", UUID.randomUUID(), operator);
        jdbc.update("INSERT INTO solicitacao_privacidade(id,protocolo,usuario_id,idempotency_key,request_hash,tipo,descricao_cifrada,estado) VALUES (?,?,?,'browser-s6',?,'EXCLUSAO',?,'PENDENTE')", request, "PRV-S6-" + request.toString().substring(0, 8), subject, protector.lookup("browser-s6"), protector.encrypt("solicitação sintética"));
    }
    private String reviewIdFor(String email) { return jdbc.queryForObject("SELECT r.id FROM revisao_entregador r JOIN entregador e ON e.id=r.entregador_id WHERE e.usuario_id=(SELECT id FROM usuario WHERE email_busca=?)", String.class, lookup(email)); }
    private void global(String email, String role) { jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES (?,?)", user(email), role); }
    private void member(String email, String role) { jdbc.update("UPDATE membro_instituicao SET papel=? WHERE usuario_id=?", role, user(email)); }
    private String account(String prefix) { String email = prefix + "-" + UUID.randomUUID() + "@example.test"; UUID id = UUID.randomUUID(); jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())", id, protector.encrypt(email), protector.lookup(email), Passwords.hash(password), protector.encrypt(prefix)); return email; }
    private UUID user(String email) { return jdbc.queryForObject("SELECT id FROM usuario WHERE email_busca=?", UUID.class, lookup(email)); }
    private byte[] lookup(String email) { return protector.lookup(email); }
    private byte[] hash(byte[] content) { try { return MessageDigest.getInstance("SHA-256").digest(content); } catch (Exception ex) { throw new IllegalStateException(ex); } }
    private String enrollMfa(String email) throws Exception { String token = login(email); JsonNode e = json.readTree(postToken(token, "/me/mfa/totp/enrollment", Map.of("password", password)).body()); return e.get("secret").asText(); }
    private String login(String email) throws Exception { return json.readTree(postToken(null, "/auth/login", Map.of("email", email, "password", password, "client", "MOBILE")).body()).get("accessToken").asText(); }
    private HttpResponse<String> get(String email, String path) throws Exception { return getToken(login(email), path); }
    private HttpResponse<String> getToken(String token, String path) throws Exception { return request(token, "GET", path, null); }
    private HttpResponse<String> postToken(String token, String path, Object body) throws Exception { return request(token, "POST", path, body); }
    private HttpResponse<String> request(String token, String method, String path, Object body) throws Exception { var b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path)).header("Content-Type", "application/json"); if (token != null) b.header("Authorization", "Bearer " + token); String payload = body == null ? null : json.writeValueAsString(body); return http.send(b.method(method, payload == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString()); }

    private static int findFreePort() { try (ServerSocket socket = new ServerSocket()) { socket.bind(new InetSocketAddress("127.0.0.1", 0)); return socket.getLocalPort(); } catch (Exception ex) { throw new IllegalStateException("Não foi possível reservar porta do ClamAV", ex); } }

    private static final class ClamAvProcess implements AutoCloseable {
        private final Process process;
        private final Path root;
        private ClamAvProcess(Process process, Path root) { this.process = process; this.root = root; }

        static ClamAvProcess start(int port) throws Exception {
            Path database = Path.of("/var/lib/clamav");
            if (!Files.isReadable(database.resolve("main.cvd")) || !Files.isReadable(database.resolve("daily.cld"))) {
                throw new IllegalStateException("Cache ClamAV válida ausente em /var/lib/clamav; o teste não baixa assinaturas automaticamente.");
            }
            Path root = Files.createTempDirectory("exame-perto-clamav-");
            Path config = root.resolve("clamd.conf");
            Files.writeString(config, "TCPSocket " + port + "\nTCPAddr 127.0.0.1\nForeground yes\nDatabaseDirectory " + database + "\nTemporaryDirectory " + root + "\nLogFile " + root.resolve("clamd.log") + "\nPidFile " + root.resolve("clamd.pid") + "\nMaxThreads 2\nMaxQueue 4\nReadTimeout 20\nCommandReadTimeout 5\nStreamMaxLength 10M\nMaxScanSize 10M\nMaxFileSize 10M\n", StandardCharsets.UTF_8);
            Process process = new ProcessBuilder("clamd", "--config-file=" + config).redirectErrorStream(true).redirectOutput(root.resolve("process.log").toFile()).start();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(90);
            while (System.nanoTime() < deadline) {
                if (!process.isAlive()) throw new IllegalStateException("ClamAV encerrou durante a inicialização: " + Files.readString(root.resolve("process.log")));
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress("127.0.0.1", port), 500);
                    socket.setSoTimeout(1000);
                    socket.getOutputStream().write("PING\0".getBytes(StandardCharsets.US_ASCII));
                    socket.getOutputStream().flush();
                    byte[] response = socket.getInputStream().readNBytes(5);
                    if ("PONG".equals(new String(response, StandardCharsets.US_ASCII).replace("\0", "").trim())) return new ClamAvProcess(process, root);
                } catch (Exception ignored) { Thread.sleep(200); }
            }
            process.destroyForcibly();
            throw new IllegalStateException("ClamAV não ficou pronto em 90s: " + Files.readString(root.resolve("process.log")));
        }

        @Override public void close() throws Exception {
            if (process.isAlive()) process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
            if (Files.exists(root)) try (var paths = Files.walk(root)) { paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception ignored) {} }); }
        }
    }
}
