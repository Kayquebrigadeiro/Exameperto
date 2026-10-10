package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@org.springframework.test.annotation.DirtiesContext
@Testcontainers
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"registration.privacy-approved=true","security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=","security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=","registration.attempts-per-minute=1000","registration.global-attempts-per-minute=5000"})
class QuoteAcceptanceFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    static final Path ROOT=Path.of(System.getProperty("java.io.tmpdir"),"exame-perto-accept-test-"+UUID.randomUUID());
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);r.add("storage.private-root",ROOT::toString);}
    @LocalServerPort int port; @MockBean EmailGateway email; @MockBean RouteProvider routes; @MockBean PaymentProvider payments; @Autowired ObjectMapper json; @Autowired JdbcTemplate jdbc;
    final HttpClient client=HttpClient.newHttpClient(); static final String PASS="Senha-sintetica-123"; final java.util.concurrent.ConcurrentHashMap<String,String> mail=new java.util.concurrent.ConcurrentHashMap<>();
    @BeforeEach void setup(){when(email.configured()).thenReturn(true);org.mockito.Mockito.doAnswer(i->{mail.put(i.getArgument(0),i.getArgument(2));return null;}).when(email).send(any(),any(),any());when(routes.route(any(),any())).thenReturn(new RouteProvider.RouteResult("synthetic-router","route-"+UUID.randomUUID(),12500,1800,false,Instant.now()));}
    @AfterAll static void cleanup()throws Exception{if(Files.exists(ROOT))try(var p=Files.walk(ROOT)){p.sorted(java.util.Comparator.reverseOrder()).forEach(x->{try{Files.deleteIfExists(x);}catch(Exception ignored){}});}}

    @Test void paymentEventLimitRunsBeforeTheProviderForChunkedBodies() throws Exception {
        byte[] oversized = new byte[65537];
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/integrations/payments/events"))
            .header("Content-Type","application/octet-stream")
            .POST(HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(oversized))).build();
        assertThat(client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(413);
        org.mockito.Mockito.verify(payments, org.mockito.Mockito.never()).authenticateAndParse(any(), any());
    }

    @Test void privateAcceptanceRequiresProviderAndAuthenticatedEventsAreDeduplicated() throws Exception {
        Account owner=active("accept-owner"), outsider=active("accept-outsider"); Setup s=order(owner,null);
        Map<String,Object> terms=terms(s);
        assertThat(request(outsider,"POST","/quotes/"+s.quote+"/accept",json.writeValueAsString(terms),"wrong-user","0").statusCode()).isEqualTo(404);
        assertThat(request(owner,"POST","/quotes/"+s.quote+"/accept",json.writeValueAsString(terms),"no-provider","0").statusCode()).isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aceite_orcamento WHERE orcamento_id=?",Long.class,s.quote)).isZero();
        when(payments.configured()).thenReturn(true);when(payments.providerId()).thenReturn("controlled-test");
        var accepted=request(owner,"POST","/quotes/"+s.quote+"/accept",json.writeValueAsString(terms),"accept-private","0");assertThat(accepted.statusCode()).as(accepted.body()).isEqualTo(202);assertThat(body(accepted).get("coverageStatus")).isEqualTo("PENDENTE_PAGAMENTO");
        assertThat(request(owner,"POST","/quotes/"+s.quote+"/accept",json.writeValueAsString(terms),"accept-private","0").statusCode()).isEqualTo(202);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM aceite_orcamento WHERE orcamento_id=?",Long.class,s.quote)).isEqualTo(1);
        UUID operation=jdbc.queryForObject("SELECT id FROM operacao_financeira WHERE pedido_id=? AND tipo='COBRANCA'",UUID.class,s.order);String beneficiary=s.patient.toString();
        when(payments.authenticateAndParse(any(),any())).thenAnswer(i->{Map<String,String> headers=i.getArgument(1);if(!"valid-test-signature".equals(headers.get("x-test-signature")))throw OrderService.error(org.springframework.http.HttpStatus.UNAUTHORIZED,"INVALID_SIGNATURE","Assinatura inválida.");String value=new String((byte[])i.getArgument(0));String[] p=value.split("\\|");return new PaymentProvider.PaymentEvent(p[0],operation,PaymentProvider.Result.valueOf(p[1]),new BigDecimal(p[2]),p[3],p[4],Instant.now());});
        assertThat(event("evt-auth|CONFIRMED|38.00|BRL|"+beneficiary,null).statusCode()).isEqualTo(401);
        assertThat(event("evt-uncertain|UNCERTAIN|38.00|BRL|"+beneficiary,"valid-test-signature").statusCode()).isEqualTo(202);assertThat(jdbc.queryForObject("SELECT estado FROM operacao_financeira WHERE id=?",String.class,operation)).isEqualTo("INCERTA");
        assertThat(event("evt-confirm|CONFIRMED|38.00|BRL|"+beneficiary,"valid-test-signature").statusCode()).isEqualTo(202);assertThat(body(request(owner,"GET","/orders/"+s.order,null,null,null)).get("coverageStatus")).isEqualTo("CONFIRMADA");
        assertThat(event("evt-confirm|CONFIRMED|38.00|BRL|"+beneficiary,"valid-test-signature").statusCode()).isEqualTo(200);
        assertThat(event("evt-confirm|FAILED|38.00|BRL|"+beneficiary,"valid-test-signature").statusCode()).isEqualTo(409);
        assertThat(event("evt-late-failure|FAILED|38.00|BRL|"+beneficiary,"valid-test-signature").statusCode()).isEqualTo(202);assertThat(jdbc.queryForObject("SELECT estado FROM operacao_financeira WHERE id=?",String.class,operation)).isEqualTo("CONFIRMADA");
        assertThat(event("evt-bad|CONFIRMED|37.99|BRL|"+beneficiary,"valid-test-signature").statusCode()).isEqualTo(409);
        assertThat(event("evt-recipient|CONFIRMED|38.00|BRL|other","valid-test-signature").statusCode()).isEqualTo(409);
        try(var pool=Executors.newFixedThreadPool(2)){var one=pool.submit(()->event("evt-concurrent|FAILED|38.00|BRL|"+beneficiary,"valid-test-signature").statusCode());var two=pool.submit(()->event("evt-concurrent|FAILED|38.00|BRL|"+beneficiary,"valid-test-signature").statusCode());assertThat(java.util.List.of(one.get(),two.get())).containsExactlyInAnyOrder(202,200);}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM evento_pagamento WHERE operacao_id=? AND estado='PROCESSADO'",Long.class,operation)).isEqualTo(2);
        org.assertj.core.api.Assertions.assertThatThrownBy(()->jdbc.update("UPDATE aceite_orcamento SET frete=1 WHERE orcamento_id=?",s.quote)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void expiredSubstitutedStaleAndRevokedFamilyCannotAccept() throws Exception {
        Account owner=active("guard-owner"), family=active("guard-family");Setup expired=order(owner,null);jdbc.update("UPDATE orcamento SET expira_em=clock_timestamp()-interval '1 second' WHERE id=?",expired.quote);when(payments.configured()).thenReturn(true);when(payments.providerId()).thenReturn("controlled-test");
        assertThat(request(owner,"POST","/quotes/"+expired.quote+"/accept",json.writeValueAsString(terms(expired)),"expired","0").statusCode()).isEqualTo(409);
        Setup stale=order(owner,null);Map<String,Object> staleTerms=terms(stale);jdbc.update("UPDATE pedido SET version=version+1 WHERE id=?",stale.order);assertThat(request(owner,"POST","/quotes/"+stale.quote+"/accept",json.writeValueAsString(staleTerms),"stale","0").statusCode()).isEqualTo(412);
        Setup substituted=order(owner,null);jdbc.update("UPDATE orcamento SET estado='SUBSTITUIDO' WHERE id=?",substituted.quote);assertThat(request(owner,"POST","/quotes/"+substituted.quote+"/accept",json.writeValueAsString(terms(substituted)),"substituted","0").statusCode()).isEqualTo(409);
        Setup noPolicy=order(owner,null);jdbc.update("UPDATE orcamento SET politica_cancelamento_id=NULL WHERE id=?",noPolicy.quote);assertThat(request(owner,"POST","/quotes/"+noPolicy.quote+"/accept",json.writeValueAsString(terms(noPolicy)),"no-policy","0").statusCode()).isEqualTo(422);
        Setup delegated=order(owner,null);grant(delegated.patient,family,Instant.now().plusSeconds(3600));jdbc.update("UPDATE autorizacao_paciente SET revogada_em=clock_timestamp() WHERE paciente_id=? AND familiar_id=?",delegated.patient,user(family.email));assertThat(request(family,"POST","/quotes/"+delegated.quote+"/accept",json.writeValueAsString(terms(delegated)),"revoked","0").statusCode()).isEqualTo(404);
    }

    @Test void subsidizedReservationsCompeteForRealBalanceAndRollbackIsAtomic() throws Exception {
        Account owner=active("subsidy-owner");UUID institution=UUID.randomUUID(),program=UUID.randomUUID();jdbc.update("INSERT INTO instituicao(id,nome,estado) VALUES (?,'Instituição sintética','HABILITADA')",institution);jdbc.update("INSERT INTO programa(id,instituicao_id,codigo,nome,estado) VALUES (?,?,'P','Programa sintético','HABILITADO')",program,institution);
        UUID policy=UUID.randomUUID();jdbc.update("INSERT INTO politica_beneficio(id,instituicao_id,codigo,versao,criterios,percentual_maximo,vigencia_inicio,estado) VALUES (?,?,'PB',1,'{}',100,clock_timestamp()-interval '1 day','ATIVA')",policy,institution);
        var patientResponse=request(owner,"POST","/me/patient",json.writeValueAsString(Map.of("cpf",digits(),"birthDate","1980-01-01")),null,null);UUID patient=UUID.fromString(body(patientResponse).get("id").toString());UUID benefit=UUID.randomUUID();jdbc.update("INSERT INTO solicitacao_beneficio(id,paciente_id,solicitante_id,instituicao_id,politica_id,politica_versao,politica_snapshot,estado,decisao,percentual,motivo_codigo,decisao_valida_ate) VALUES (?,?,?,?,?,1,'{}','DECIDIDA','APROVADA',100,'TESTE',clock_timestamp()+interval '1 day')",benefit,patient,user(owner.email),institution,policy);
        Setup first=orderForPatient(owner,patient,program);
        Setup second=orderForPatient(owner,first.patient,program);
        jdbc.update("INSERT INTO conta_programa(programa_id,disponivel) VALUES (?,38.00) ON CONFLICT (programa_id) DO UPDATE SET disponivel=38.00,reservado=0",program);
        final Setup a=first,b=second;try(var pool=Executors.newFixedThreadPool(2)){var fa=pool.submit(()->request(owner,"POST","/quotes/"+a.quote+"/accept",json.writeValueAsString(terms(a)),"reserve-a","0").statusCode());var fb=pool.submit(()->request(owner,"POST","/quotes/"+b.quote+"/accept",json.writeValueAsString(terms(b)),"reserve-b","0").statusCode());assertThat(java.util.List.of(fa.get(),fb.get())).containsExactlyInAnyOrder(202,409);}
        assertThat(jdbc.queryForObject("SELECT disponivel FROM conta_programa WHERE programa_id=?",BigDecimal.class,program)).isEqualByComparingTo("0.00");assertThat(jdbc.queryForObject("SELECT reservado FROM conta_programa WHERE programa_id=?",BigDecimal.class,program)).isEqualByComparingTo("38.00");assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva_subsidio WHERE programa_id=?",Long.class,program)).isEqualTo(1);
        UUID acceptedOrder=jdbc.queryForObject("SELECT pedido_id FROM reserva_subsidio WHERE programa_id=?",UUID.class,program);long version=jdbc.queryForObject("SELECT version FROM pedido WHERE id=?",Long.class,acceptedOrder);request(owner,"PUT","/orders/"+acceptedOrder+"/addresses",json.writeValueAsString(Map.of("origin",address("01001000",-23.5,-46.6),"destination",address("20040002",-22.8,-43.1),"version",version)),null,null);assertThat(jdbc.queryForObject("SELECT disponivel FROM conta_programa WHERE programa_id=?",BigDecimal.class,program)).isEqualByComparingTo("38.00");
        // Force the last insert to fail and verify reservation/account/acceptance all roll back.
        Setup rollback=orderForPatient(owner,first.patient,program);jdbc.execute("CREATE OR REPLACE FUNCTION fail_accept_test() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced rollback'; END $$");jdbc.execute("CREATE TRIGGER fail_accept_test BEFORE INSERT ON aceite_idempotencia FOR EACH ROW EXECUTE FUNCTION fail_accept_test()");
        assertThat(request(owner,"POST","/quotes/"+rollback.quote+"/accept",json.writeValueAsString(terms(rollback)),"forced-conflict","0").statusCode()).isEqualTo(500);jdbc.execute("DROP TRIGGER fail_accept_test ON aceite_idempotencia");jdbc.execute("DROP FUNCTION fail_accept_test()");assertThat(jdbc.queryForObject("SELECT count(*) FROM reserva_subsidio WHERE orcamento_id=?",Long.class,rollback.quote)).isZero();assertThat(jdbc.queryForObject("SELECT disponivel FROM conta_programa WHERE programa_id=?",BigDecimal.class,program)).isEqualByComparingTo("38.00");
    }

    private Setup order(Account owner,UUID program)throws Exception{UUID patient;var existing=jdbc.query("SELECT id FROM paciente WHERE usuario_id=?",(rs,n)->rs.getObject(1,UUID.class),user(owner.email));if(existing.isEmpty()){var p=request(owner,"POST","/me/patient",json.writeValueAsString(Map.of("cpf",digits(),"birthDate","1980-01-01")),null,null);patient=UUID.fromString(body(p).get("id").toString());}else patient=existing.getFirst();return orderForPatient(owner,patient,program);}
    private Setup orderForPatient(Account owner,UUID patient,UUID program)throws Exception{UUID doc=UUID.randomUUID(),order=UUID.randomUUID(),auth=UUID.randomUUID(),unit=UUID.randomUUID();jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?, 'AUTORIZACAO_RETIRADA',?,'12345678901234567890123456789012','application/pdf',100,'QUARENTENA')",doc,user(owner.email),"synthetic/"+doc);Map<String,Object> in=Map.of("patientId",patient,"recipientUserId",user(owner.email),"pickupUnitId",unit,"pickupAuthorizationDocumentId",doc,"origin",address("01001000",-23.55,-46.63),"destination",address("20040002",-22.90,-43.17));var created=request(owner,"POST","/orders",json.writeValueAsString(in),UUID.randomUUID().toString(),null);order=UUID.fromString(body(created).get("id").toString());jdbc.update("UPDATE autorizacao_retirada SET estado='VERIFICADA' WHERE pedido_id=?",order);seedPolicies();return quoteExisting(owner,order,patient,program,0);}
    private Setup quoteExisting(Account owner,UUID order,UUID patient,UUID program,long ignored)throws Exception{long version=jdbc.queryForObject("SELECT version FROM pedido WHERE id=?",Long.class,order);var q=request(owner,"POST","/orders/"+order+"/quotes",json.writeValueAsString(program==null?Map.of():Map.of("programId",program)),UUID.randomUUID().toString(),String.valueOf(version));assertThat(q.statusCode()).as(q.body()).isEqualTo(201);return new Setup(patient,order,UUID.fromString(body(q).get("id").toString()),((Number)body(q).get("grossAmount")).toString(),((Number)body(q).get("patientAmount")).toString(),UUID.fromString(body(q).get("cancellationPolicyId").toString()),jdbc.queryForObject("SELECT version FROM pedido WHERE id=?",Long.class,order));}
    private void seedPolicies(){if(jdbc.queryForObject("SELECT count(*) FROM tarifa WHERE estado='ATIVA'",Long.class)==0)jdbc.update("INSERT INTO tarifa(id,numero,formula_codigo,parametros,inicio,estado) VALUES (?,1,'BASE_KM_MINUTO','{\"base\":\"10.00\",\"perKm\":\"2.00\",\"perMinute\":\"0.10\",\"minimum\":\"5.00\",\"validitySeconds\":900}',clock_timestamp(),'ATIVA')",UUID.randomUUID());if(jdbc.queryForObject("SELECT count(*) FROM politica_cancelamento WHERE estado='ATIVA'",Long.class)==0)jdbc.update("INSERT INTO politica_cancelamento(id,numero,criterios,inicio,estado) VALUES (?,1,'{}',clock_timestamp(),'ATIVA')",UUID.randomUUID());}
    private Map<String,Object> terms(Setup s){return Map.of("acceptedGrossAmount",s.gross,"acceptedPatientAmount",s.patientAmount,"acceptedCurrency","BRL","acceptedCancellationPolicyId",s.cancellation,"acceptedOrderVersion",s.orderVersion);}
    private void grant(UUID patient,Account family,Instant expires){UUID invitation=UUID.randomUUID(),grant=UUID.randomUUID();jdbc.update("INSERT INTO convite_familiar(id,paciente_id,destinatario_email_busca,token_hash,estado,expira_em,consumido_em,aceito_por,aceito_em) VALUES (?,?,?,?,'CONFIRMADO',?,clock_timestamp(),?,clock_timestamp())",invitation,patient,lookup(family.email),lookup("token-"+invitation),java.sql.Timestamp.from(expires),user(family.email));jdbc.update("INSERT INTO autorizacao_paciente(id,paciente_id,familiar_id,concedida_por,convite_id,confirmada_em,expira_em) VALUES (?,?,?,?,?,clock_timestamp(),?)",grant,patient,user(family.email),jdbc.queryForObject("SELECT usuario_id FROM paciente WHERE id=?",UUID.class,patient),invitation,java.sql.Timestamp.from(expires));jdbc.update("INSERT INTO autorizacao_escopo(autorizacao_id,escopo) VALUES (?, 'PEDIDOS')",grant);}
    private HttpResponse<String> event(String body,String signature)throws Exception{var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1/integrations/payments/events")).header("Content-Type","application/octet-stream");if(signature!=null)request.header("X-Test-Signature",signature);return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
    private Map<String,Object> address(String postal,double lat,double lon){return Map.of("street","Rua sintética","number","10","district","Centro","city","Cidade","state","SP","postalCode",postal,"latitude",lat,"longitude",lon);}
    private String digits(){return String.format("%011d",Math.abs(UUID.randomUUID().getLeastSignificantBits())%100000000000L);}
    private Account active(String prefix)throws Exception{String e=prefix+UUID.randomUUID()+"@example.test";var r=request(null,"POST","/auth/register",json.writeValueAsString(Map.of("name","Sintético","email",e,"password",PASS)),null,null);String sent=mail.get(e);String token=sent.substring(sent.lastIndexOf(':')+2).trim();request(null,"POST","/auth/verification",json.writeValueAsString(Map.of("token",token)),null,null);var login=request(null,"POST","/auth/login",json.writeValueAsString(Map.of("email",e,"password",PASS,"client","MOBILE")),null,null);return new Account(e,body(login).get("accessToken").toString());}
    private UUID user(String email){return jdbc.queryForObject("SELECT id FROM usuario WHERE email_busca=?",UUID.class,lookup(email));}
    private HttpResponse<String> request(Account a,String method,String path,String body,String idem,String etag)throws Exception{var b=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).header("Content-Type","application/json");if(a!=null)b.header("Authorization","Bearer "+a.access);if(idem!=null)b.header("Idempotency-Key",idem);if(etag!=null)b.header("If-Match",etag);return client.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
    private Map<String,Object> body(HttpResponse<String> r)throws Exception{return json.readValue(r.body(),new TypeReference<>(){});}
    private byte[] lookup(String value){try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(java.util.Base64.getDecoder().decode("AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="),"HmacSHA256"));return mac.doFinal(value.getBytes());}catch(Exception e){throw new RuntimeException(e);}}
    record Account(String email,String access){} record Setup(UUID patient,UUID order,UUID quote,String gross,String patientAmount,UUID cancellation,long orderVersion){}
}
