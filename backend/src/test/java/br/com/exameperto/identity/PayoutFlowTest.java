package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;

@DirtiesContext @Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"registration.privacy-approved=true","registration.allowed-origin=http://127.0.0.1:5187","security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=","security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=","registration.attempts-per-minute=1000","registration.global-attempts-per-minute=5000"})
class PayoutFlowTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",DB::getJdbcUrl);r.add("spring.datasource.username",DB::getUsername);r.add("spring.datasource.password",DB::getPassword);}
    @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired DataProtector protector;@Autowired ObjectMapper json;@Autowired PayoutService payouts;@Autowired TransactionTemplate tx;@MockBean PayoutProvider provider;
    HttpClient http=HttpClient.newHttpClient();String password="Senha-sintetica-123";

    @BeforeEach void provider(){when(provider.configured()).thenReturn(true);when(provider.providerId()).thenReturn("controlled-test");}

    @Test void timeoutLateConfirmationDuplicatesOrderingAndReconciliationDoNotDoublePay() throws Exception {
        Account manager=account("manager"),driverUser=account("driver"),patient=account("patient");Fixture f=fixture(manager,driverUser,patient,"42.50");mfa(manager);
        var initial=request(driverUser,"GET","/orders/"+f.order+"/payout",null,null);assertThat(initial.statusCode()).isEqualTo(200);assertThat(body(initial).get("status")).isEqualTo("OBRIGACAO_REGISTRADA");
        assertThat(jdbc.queryForObject("SELECT valor_devido FROM apuracao_remuneracao WHERE pedido_id=?",BigDecimal.class,f.order)).isEqualByComparingTo("42.50");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operacao_financeira WHERE pedido_id=? AND tipo='REPASSE'",Long.class,f.order)).isZero();
        when(provider.request(anyString(),any(),eq("BRL"),anyString())).thenThrow(new RuntimeException("synthetic timeout"));
        var uncertain=request(manager,"POST","/payouts/"+f.payout+"/request",null,"request-1");assertThat(uncertain.statusCode()).as(uncertain.body()).isEqualTo(200);assertThat(body(uncertain).get("status")).isEqualTo("INCERTO");
        String reference=jdbc.queryForObject("SELECT referencia FROM repasse WHERE id=?",String.class,f.payout);assertThat(reference).isEqualTo("repasse:"+f.order);
        when(provider.query(reference)).thenReturn(outcome("late-confirm",reference,PayoutProvider.Result.CONFIRMED,"42.50","BRL",f.recipient,Instant.now()));
        var confirmed=request(manager,"POST","/payouts/"+f.payout+"/reconcile",null,"reconcile-1");assertThat(confirmed.statusCode()).as(confirmed.body()).isEqualTo(200);assertThat(body(confirmed).get("status")).isEqualTo("CONFIRMADO");
        var replay=request(manager,"POST","/payouts/"+f.payout+"/reconcile",null,"reconcile-1");assertThat(replay.statusCode()).isEqualTo(200);
        verify(provider,times(1)).request(eq(reference),any(),eq("BRL"),eq(f.recipient));verify(provider,times(1)).query(reference);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM lancamento_financeiro l JOIN operacao_financeira o ON o.id=l.operacao_id WHERE o.pedido_id=? AND o.tipo='REPASSE'",Long.class,f.order)).isEqualTo(2);
        payouts.applyOutcome(f.payout,outcome("older-failure",reference,PayoutProvider.Result.FAILED,"42.50","BRL",f.recipient,Instant.now().minusSeconds(60)),new byte[]{1});
        payouts.applyOutcome(f.payout,outcome("older-failure",reference,PayoutProvider.Result.FAILED,"42.50","BRL",f.recipient,Instant.now().minusSeconds(60)),new byte[]{1});
        assertThat(jdbc.queryForObject("SELECT estado FROM repasse WHERE id=?",String.class,f.payout)).isEqualTo("CONFIRMADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_repasse WHERE repasse_id=? AND resultado='IGNORADO_FORA_ORDEM'",Long.class,f.payout)).isEqualTo(1);
    }

    @Test void divergenceMfaTenantConcurrencyAndUnavailableProviderAreEnforced() throws Exception {
        Account manager=account("manager2"),other=account("other"),driver=account("driver2"),patient=account("patient2");Fixture f=fixture(manager,driver,patient,"31.20");
        assertThat(request(manager,"GET","/financial/payouts",null,null).statusCode()).isEqualTo(403);
        mfa(manager);UUID foreignInstitution=UUID.randomUUID();jdbc.update("INSERT INTO instituicao(id,nome,estado) VALUES (?,?,'HABILITADA')",foreignInstitution,"Outra");grant(other,foreignInstitution);mfa(other);
        assertThat(request(other,"GET","/payouts/"+f.payout,null,null).statusCode()).isEqualTo(403);
        String ref="repasse:"+f.order;when(provider.request(anyString(),any(),anyString(),anyString())).thenReturn(outcome("divergent",ref,PayoutProvider.Result.CONFIRMED,"31.20","USD",f.recipient,Instant.now()));
        ExecutorService pool=Executors.newFixedThreadPool(2);try{java.util.List<Callable<HttpResponse<String>>> tasks=java.util.List.of(()->request(manager,"POST","/payouts/"+f.payout+"/request",null,"same-key"),()->request(manager,"POST","/payouts/"+f.payout+"/request",null,"same-key"));var calls=pool.invokeAll(tasks);assertThat(calls.stream().map(x->{try{return x.get().statusCode();}catch(Exception e){throw new RuntimeException(e);}}).toList()).allMatch(s->s==200||s==409);}finally{pool.shutdownNow();}
        assertThat(jdbc.queryForObject("SELECT estado FROM repasse WHERE id=?",String.class,f.payout)).isEqualTo("DIVERGENTE");assertThat(jdbc.queryForObject("SELECT divergencia_codigo FROM repasse WHERE id=?",String.class,f.payout)).isEqualTo("CURRENCY_MISMATCH");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operacao_financeira WHERE pedido_id=? AND tipo='REPASSE'",Long.class,f.order)).isEqualTo(1);
        when(provider.configured()).thenReturn(false);Fixture unavailable=fixture(manager,account("driver3"),account("patient3"),"19.90");var denied=request(manager,"POST","/payouts/"+unavailable.payout+"/request",null,"no-provider");assertThat(denied.statusCode()).isEqualTo(503);assertThat(jdbc.queryForObject("SELECT estado FROM repasse WHERE id=?",String.class,unavailable.payout)).isEqualTo("OBRIGACAO_REGISTRADA");
    }

    @Test void failedSettlementRollsBackWithoutPartialEntriesAndUnapprovedCasesStayBlocked(){Account manager=account("manager3"),driver=account("driver4"),patient=account("patient4");Fixture f=fixture(manager,driver,patient,"25.00");long operations=jdbc.queryForObject("SELECT count(*) FROM operacao_financeira WHERE pedido_id=?",Long.class,f.order);assertThatThrownBy(()->tx.executeWithoutResult(s->payouts.recordCompletedService(f.order))).isInstanceOf(RuntimeException.class);assertThat(jdbc.queryForObject("SELECT count(*) FROM operacao_financeira WHERE pedido_id=?",Long.class,f.order)).isEqualTo(operations);assertThat(jdbc.queryForObject("SELECT count(*) FROM apuracao_remuneracao WHERE pedido_id=?",Long.class,f.order)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT count(*) FROM apuracao_remuneracao WHERE modalidade<>'SERVICO_COMPLETO' AND estado='CONCLUIDA'",Long.class)).isZero();}
    @Test @EnabledIfSystemProperty(named="browserTest",matches="true") void browserRendersRealFinancialPanel() throws Exception {Account manager=account("browser-manager"),driver=account("browser-driver"),patient=account("browser-patient");fixture(manager,driver,patient,"25.00");mfa(manager);ProcessBuilder process=new ProcessBuilder("npm","run","test","--","tests/payout-real.spec.ts");process.directory(Path.of("..","web").toFile());process.redirectErrorStream(true);process.redirectOutput(Path.of("target","payout-browser-playwright.log").toFile());process.environment().put("API_TARGET","http://127.0.0.1:"+port);process.environment().put("VITE_ENABLE_OPERATIONAL_TEST_PANELS","true");process.environment().put("PAYOUT_MANAGER_EMAIL",manager.email);process.environment().put("PAYOUT_MANAGER_TOKEN",manager.token);Process child=process.start();assertThat(child.waitFor(120,TimeUnit.SECONDS)).isTrue();assertThat(child.exitValue()).isZero();}

    private Fixture fixture(Account manager,Account driverUser,Account patientUser,String amount){UUID institution=UUID.randomUUID(),program=UUID.randomUUID();jdbc.update("INSERT INTO instituicao(id,nome,estado) VALUES (?,?,'HABILITADA')",institution,"Instituição sintética");jdbc.update("INSERT INTO programa(id,instituicao_id,codigo,nome,estado) VALUES (?,?,?,?,'HABILITADO')",program,institution,"P"+UUID.randomUUID(),"Programa");grant(manager,institution);UUID patient=UUID.randomUUID();jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,DATE '1980-01-01','VERIFICADA')",patient,patientUser.id,protector.encrypt(String.format("%011d",Math.abs(patient.hashCode()))),protector.lookup(String.format("%011d",Math.abs(patient.hashCode()))));UUID deliverer=UUID.randomUUID(),vehicle=UUID.randomUUID(),link=UUID.randomUUID();jdbc.update("INSERT INTO entregador(id,usuario_id,nascimento,estado) VALUES (?,?,DATE '1990-01-01','APROVADO')",deliverer,driverUser.id);jdbc.update("INSERT INTO veiculo(id,placa,marca,modelo,cor,ano_fabricacao,ano_modelo,estado) VALUES (?,?,'M','M','A',2024,2024,'APROVADO')",vehicle,"P"+UUID.randomUUID().toString().substring(0,7));jdbc.update("INSERT INTO vinculo_veiculo(id,entregador_id,veiculo_id,tipo,estado,valido_ate,version) VALUES (?,?,?,'PROPRIEDADE','APROVADO',current_date+10,1)",link,deliverer,vehicle);UUID tariff=UUID.randomUUID(),cancel=UUID.randomUUID(),policy=UUID.randomUUID(),order=UUID.randomUUID(),quote=UUID.randomUUID(),assignment=UUID.randomUUID();int n=Math.abs(UUID.randomUUID().hashCode()%1000000)+1;jdbc.update("INSERT INTO tarifa(id,numero,formula_codigo,parametros,inicio,estado) VALUES (?,?,'APPROVED','{}',clock_timestamp(),'ATIVA')",tariff,n);jdbc.update("INSERT INTO politica_cancelamento(id,numero,criterios,inicio,estado) VALUES (?,?,'{}',clock_timestamp(),'ATIVA')",cancel,n+1);jdbc.update("INSERT INTO politica_designacao(id,numero,limite_tarefas_simultaneas,inicio,estado) VALUES (?,?,1,clock_timestamp(),'ATIVA')",policy,n+2);byte[] encrypted=protector.encrypt("isolated");jdbc.update("INSERT INTO pedido(id,paciente_id,solicitante_id,origem_cifrada,destino_cifrada,origem_lat,origem_lon,destino_lat,destino_lon,destinatario_id,unidade_id,estado,version) VALUES (?,?,?,?,?,-23,-46,-23,-46,?,?,'ENTREGUE',6)",order,patient,patientUser.id,encrypted,encrypted,patientUser.id,UUID.randomUUID());jdbc.update("INSERT INTO orcamento(id,pedido_id,paciente_id,tarifa_id,rota_provedor,rota_referencia,distancia_m,duracao_s,rota_calculada_em,transito_incluido,politica_snapshot,frete,paciente_valor,subsidio_valor,moeda,expira_em,aceito_em,estado,programa_id,politica_cancelamento_id,pedido_version,version) VALUES (?,?,?,?,'test','route',100,60,clock_timestamp(),false,'{}',?,0,?,'BRL',clock_timestamp()+interval '1 hour',clock_timestamp(),'ACEITO',?,?,1,1)",quote,order,patient,tariff,new BigDecimal(amount),new BigDecimal(amount),program,cancel);jdbc.update("INSERT INTO conta_programa(programa_id,disponivel,reservado,moeda) VALUES (?,0,?,'BRL')",program,new BigDecimal(amount));jdbc.update("INSERT INTO reserva_subsidio(id,orcamento_id,pedido_id,programa_id,paciente_id,valor,moeda,estado) VALUES (?,?,?,?,?,?,'BRL','RESERVADA')",UUID.randomUUID(),quote,order,program,patient,new BigDecimal(amount));jdbc.update("INSERT INTO designacao(id,pedido_id,entregador_id,vinculo_id,orcamento_id,politica_designacao_id,politica_cancelamento_id,pedido_version,limite_tarefas_snapshot,identificacao_snapshot,encerrada_em,encerramento_motivo) VALUES (?,?,?,?,?,?,?,2,1,'{}',clock_timestamp(),'ENTREGA_COMPROVADA')",assignment,order,deliverer,link,quote,policy,cancel);tx.executeWithoutResult(s->payouts.recordCompletedService(order));UUID payout=jdbc.queryForObject("SELECT id FROM repasse WHERE pedido_id=?",UUID.class,order);return new Fixture(order,payout,"entregador:"+deliverer);}
    private void grant(Account a,UUID institution){jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES (?,'GESTOR_FINANCEIRO') ON CONFLICT DO NOTHING",a.id);jdbc.update("INSERT INTO membro_instituicao(instituicao_id,usuario_id,papel) VALUES (?,?,'GESTOR_FINANCEIRO')",institution,a.id);}
    private Account account(String prefix){UUID id=UUID.randomUUID();String email=prefix+id+"@example.test";jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",id,protector.encrypt(email),protector.lookup(email),Passwords.hash(password),protector.encrypt(prefix));try{var response=requestRaw(null,"POST","/auth/login",json.writeValueAsString(Map.of("email",email,"password",password,"client","MOBILE")),null);return new Account(id,json.readTree(response.body()).get("accessToken").asText(),email);}catch(Exception ex){throw new RuntimeException(ex);}}
    private void mfa(Account a)throws Exception{var enrolled=request(a,"POST","/me/mfa/totp/enrollment",Map.of("password",password),null);String secret=json.readTree(enrolled.body()).get("secret").asText();String code=MfaService.totp(MfaService.decodeBase32(secret),Instant.now().getEpochSecond()/30);assertThat(request(a,"POST","/me/mfa/totp/confirmation",Map.of("code",code),null).statusCode()).isEqualTo(200);}
    private PayoutProvider.Outcome outcome(String id,String ref,PayoutProvider.Result result,String amount,String currency,String recipient,Instant at){return new PayoutProvider.Outcome(id,ref,result,new BigDecimal(amount),currency,recipient,at);}
    private HttpResponse<String> request(Account a,String method,String path,Object value,String key)throws Exception{return requestRaw(a,method,path,value==null?null:json.writeValueAsString(value),key);}
    private HttpResponse<String> requestRaw(Account a,String method,String path,String value,String key)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).header("Content-Type","application/json");if(a!=null)b.header("Authorization","Bearer "+a.token);if(key!=null)b.header("Idempotency-Key",key);return http.send(b.method(method,value==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(value)).build(),HttpResponse.BodyHandlers.ofString());}
    private Map<String,Object> body(HttpResponse<String> r)throws Exception{return json.readValue(r.body(),new TypeReference<>(){});}
    record Account(UUID id,String token,String email){}record Fixture(UUID order,UUID payout,String recipient){}
}
