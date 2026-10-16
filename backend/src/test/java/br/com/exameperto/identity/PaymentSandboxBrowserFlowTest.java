package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

@Testcontainers
@EnabledIfSystemProperty(named = "browserTest", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "registration.privacy-approved=true",
    "registration.allowed-origin=http://127.0.0.1:5187",
    "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="
})
class PaymentSandboxBrowserFlowTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17.6");
    static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "exame-perto-payment-browser-" + UUID.randomUUID());

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("storage.private-root", ROOT::toString);
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataProtector protector;
    @MockBean PaymentProvider payments;

    @AfterAll
    static void cleanup() throws Exception {
        if (Files.exists(ROOT)) try (var paths = Files.walk(ROOT)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception ignored) { }
            });
        }
    }

    @Test
    void pacientePreparaConsultaEConfirmaPagamentoNoNavegador() throws Exception {
        when(payments.configured()).thenReturn(true);
        when(payments.providerId()).thenReturn("controlled-browser-sandbox");
        when(payments.createCheckout(any())).thenAnswer(invocation -> {
            PaymentProvider.CheckoutRequest request = invocation.getArgument(0);
            return new PaymentProvider.Checkout("sandbox-order-" + request.operationId(),
                "https://sandbox.mercadopago.test/checkout/" + request.operationId(),
                Instant.now().plusSeconds(3600));
        });
        when(payments.query(any(), any())).thenAnswer(invocation -> {
            PaymentProvider.CheckoutRequest request = invocation.getArgument(1);
            return new PaymentProvider.PaymentEvent("sandbox-event-" + request.operationId(), request.operationId(),
                PaymentProvider.Result.CONFIRMED, request.amount(), request.currency(),
                request.beneficiaryReference(), Instant.now());
        });

        String email = "payment-browser-" + UUID.randomUUID() + "@example.test";
        UUID user = UUID.randomUUID(), patient = UUID.randomUUID(), document = UUID.randomUUID();
        UUID order = UUID.randomUUID(), authorization = UUID.randomUUID(), tariff = UUID.randomUUID();
        UUID policy = UUID.randomUUID(), quote = UUID.randomUUID();
        jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",
            user, protector.encrypt(email), protector.lookup(email), Passwords.hash("Senha-sintetica-123"), protector.encrypt("Paciente navegador"));
        jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,?,'VERIFICADA')",
            patient, user, protector.encrypt("12345678901"), protector.lookup("12345678901"), java.time.LocalDate.of(1980, 1, 1));
        jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?, 'AUTORIZACAO_RETIRADA',?,'12345678901234567890123456789012','application/pdf',100,'QUARENTENA')",
            document, user, "synthetic/" + document);
        byte[] address = protector.encrypt("{\"street\":\"Rua sintética\",\"number\":\"10\",\"district\":\"Centro\",\"city\":\"Cidade\",\"state\":\"SP\",\"postalCode\":\"01001000\",\"latitude\":-23.55,\"longitude\":-46.63}");
        jdbc.update("INSERT INTO pedido(id,paciente_id,solicitante_id,origem_cifrada,destino_cifrada,origem_lat,origem_lon,destino_lat,destino_lon,destinatario_id,unidade_id,estado,version) VALUES (?,?,?,?,?,-23.55,-46.63,-22.90,-43.17,?,?,'AGUARDANDO_ACEITE',1)",
            order, patient, user, address, address, user, UUID.randomUUID());
        jdbc.update("INSERT INTO autorizacao_retirada(id,pedido_id,documento_id,estado,criada_por,valida_ate) VALUES (?,?,?,'VERIFICADA',?,clock_timestamp()+interval '1 day')",
            authorization, order, document, user);
        jdbc.update("INSERT INTO tarifa(id,numero,formula_codigo,parametros,inicio,estado) VALUES (?,1,'BASE_KM_MINUTO','{}',clock_timestamp(),'ATIVA')", tariff);
        jdbc.update("INSERT INTO politica_cancelamento(id,numero,criterios,inicio,estado) VALUES (?,1,'{}',clock_timestamp(),'ATIVA')", policy);
        jdbc.update("INSERT INTO orcamento(id,pedido_id,paciente_id,tarifa_id,rota_provedor,rota_referencia,distancia_m,duracao_s,rota_calculada_em,transito_incluido,politica_snapshot,frete,paciente_valor,subsidio_valor,moeda,expira_em,estado,politica_cancelamento_id,pedido_version) VALUES (?,?,?,?, 'controlled-route','route-browser',12500,1800,clock_timestamp(),false,'{}',38,38,0,'BRL',clock_timestamp()+interval '15 minutes','PROPOSTO',?,1)",
            quote, order, patient, tariff, policy);

        var process = new ProcessBuilder("npm", "run", "test", "--", "tests/payment-sandbox-real.spec.ts");
        process.directory(Path.of("..", "web").toFile());
        process.redirectErrorStream(true);
        process.redirectOutput(Path.of("target", "payment-sandbox-browser-playwright.log").toFile());
        process.environment().put("API_TARGET", "http://127.0.0.1:" + port);
        process.environment().put("VITE_ENABLE_OPERATIONAL_TEST_PANELS", "true");
        process.environment().put("ACCEPTANCE_EMAIL", email);
        process.environment().put("ACCEPTANCE_ORDER_ID", order.toString());
        Process playwright = process.start();
        assertThat(playwright.waitFor(180, TimeUnit.SECONDS)).isTrue();
        assertThat(playwright.exitValue()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aceite_orcamento WHERE orcamento_id=?", Long.class, quote)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM operacao_financeira WHERE pedido_id=?", String.class, order)).isEqualTo("CONFIRMADA");
        assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE id=?", String.class, order)).isEqualTo("DISPONIVEL");
        assertThat(jdbc.queryForObject("SELECT checkout_url LIKE 'https://sandbox.mercadopago.test/%' FROM operacao_financeira WHERE pedido_id=?", Boolean.class, order)).isTrue();
    }
}
