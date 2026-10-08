package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DirtiesContext
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "registration.privacy-approved=true",
    "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
    "registration.attempts-per-minute=1000",
    "registration.global-attempts-per-minute=5000",
    "privacy.purge-enabled=true"
})
class PrivacyFlowTest {
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",DB::getJdbcUrl);
        registry.add("spring.datasource.username",DB::getUsername);
        registry.add("spring.datasource.password",DB::getPassword);
        registry.add("storage.private-root",()->java.nio.file.Path.of(System.getProperty("java.io.tmpdir"),"exame-perto-privacy-objects-"+UUID.randomUUID()).toString());
    }

    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataProtector protector;
    @Autowired ObjectMapper json;
    @Autowired PrivateObjectStore objects;
    final HttpClient http=HttpClient.newHttpClient();
    final String password="Senha-sintetica-123";

    @Test @Order(3)
    void onlyTheAccountCanCreateAndFollowItsProtocolAndRevokedSessionStopsAccess() throws Exception {
        Account owner=account("titular");
        Account unrelated=account("outra-conta");
        Account familyWithoutScope=account("familiar-sem-escopo");
        linkFamilyWithoutPrivacyScope(owner,familyWithoutScope);
        Account operator=account("operador-resposta");
        grantOperator(operator);
        mfa(operator);

        JsonNode access=create(owner,"ACESSO","Quero acessar meus dados.","access-1");
        JsonNode correction=create(owner,"CORRECAO","Quero corrigir meu contato.","correction-1");
        JsonNode deletion=create(owner,"EXCLUSAO","Quero encerrar a conta.","delete-1");
        assertThat(access.get("protocol").asText()).startsWith("PRV-");
        assertThat(correction.get("status").asText()).isEqualTo("PENDENTE");
        assertThat(request(unrelated,"GET","/me/privacy-requests/"+deletion.get("id").asText(),null,null).statusCode()).isEqualTo(404);
        assertThat(request(familyWithoutScope,"GET","/me/privacy-requests/"+deletion.get("id").asText(),null,null).statusCode()).isEqualTo(404);
        assertThat(request(unrelated,"POST","/privacy-requests/"+access.get("id").asText()+"/response",Map.of("status","RESPONDIDA","response","indevida"),null).statusCode()).isEqualTo(403);

        HttpResponse<String> accessResponse=request(operator,"POST","/privacy-requests/"+access.get("id").asText()+"/response",Map.of("status","RESPONDIDA","response","Cópia preparada para o canal autorizado."),null);
        assertThat(accessResponse.statusCode()).as(accessResponse.body()).isEqualTo(200);
        HttpResponse<String> correctionResponse=request(operator,"POST","/privacy-requests/"+correction.get("id").asText()+"/response",Map.of("status","RESPONDIDA","response","Correção analisada; confirmação registrada."),null);
        assertThat(correctionResponse.statusCode()).as(correctionResponse.body()).isEqualTo(200);
        JsonNode followedAccess=json.readTree(request(owner,"GET","/me/privacy-requests/"+access.get("id").asText(),null,null).body());
        JsonNode followedCorrection=json.readTree(request(owner,"GET","/me/privacy-requests/"+correction.get("id").asText(),null,null).body());
        assertThat(followedAccess.get("status").asText()).isEqualTo("RESPONDIDA");
        assertThat(followedAccess.get("response").asText()).isEqualTo("Cópia preparada para o canal autorizado.");
        assertThat(followedCorrection.get("status").asText()).isEqualTo("RESPONDIDA");
        assertThat(followedCorrection.get("response").asText()).isEqualTo("Correção analisada; confirmação registrada.");

        JsonNode replay=create(owner,"EXCLUSAO","Quero encerrar a conta.","delete-1");
        assertThat(replay.get("id").asText()).isEqualTo(deletion.get("id").asText());
        assertThat(request(owner,"POST","/me/privacy-requests",Map.of("type","ACESSO","description","conteúdo diferente"),"delete-1").statusCode()).isEqualTo(409);

        jdbc.update("UPDATE sessao SET revogada_em=clock_timestamp() WHERE id=?",owner.sessionId);
        assertThat(request(owner,"GET","/me/privacy-requests/"+deletion.get("id").asText(),null,null).statusCode()).isEqualTo(401);
    }

    @Test @Order(2)
    void purgeIsAuthorizedExecutedRetriedAndVerifiedWithoutDeletingFinancialEvidence() throws Exception {
        Account owner=account("purge-owner");
        Account operator=account("privacy-operator");
        grantOperator(operator);
        mfa(operator);
        UUID ordinaryDocument=document(owner,"IDENTIDADE","ordinary.pdf");
        UUID financialDocument=document(owner,"FINANCIAMENTO","financial.pdf");
        FinancialFixture financial=financialFixture(owner,account("tracking-driver"));
        String ordinaryKey=jdbc.queryForObject("SELECT objeto_chave FROM documento WHERE id=?",String.class,ordinaryDocument);
        String financialKey=jdbc.queryForObject("SELECT objeto_chave FROM documento WHERE id=?",String.class,financialDocument);
        UUID queued=UUID.randomUUID();
        jdbc.update("INSERT INTO outbox(id,tipo,chave,payload_saneado,estado,disponivel_em) VALUES (?,'EMAIL_VERIFICACAO',?,jsonb_build_object('usuarioId',?::text),'PENDENTE',clock_timestamp())",queued,"privacy:"+queued,owner.id.toString());

        JsonNode request=create(owner,"EXCLUSAO","Solicito exclusão da conta.","purge-request");
        UUID requestId=UUID.fromString(request.get("id").asText());
        HttpResponse<String> answered=request(operator,"POST","/privacy-requests/"+requestId+"/response",Map.of("status","RESPONDIDA","response","Solicitação identificada e analisada."),null);
        assertThat(answered.statusCode()).as(answered.body()).isEqualTo(200);
        assertThat(json.readTree(answered.body()).get("status").asText()).isEqualTo("RESPONDIDA");
        HttpResponse<String> authorized=request(operator,"POST","/privacy-requests/"+requestId+"/purge",null,null);
        assertThat(authorized.statusCode()).as(authorized.body()).isEqualTo(200);
        assertThat(json.readTree(authorized.body()).get("status").asText()).isEqualTo("EXPURGO_SOLICITADO");

        UUID execution=jdbc.queryForObject("SELECT expurgo_id FROM solicitacao_privacidade WHERE id=?",UUID.class,requestId);
        jdbc.update("INSERT INTO expurgo_alvo(id,execucao_id,tipo,referencia_cifrada,estado) VALUES (?,?,'OBJETO',?,'PENDENTE')",UUID.randomUUID(),execution,protector.encrypt("../invalid-object"));
        jdbc.update("UPDATE papel_global SET revogado_em=clock_timestamp() WHERE usuario_id=?",operator.id);
        assertThat(request(operator,"POST","/privacy-requests/"+requestId+"/purge/execute",null,null).statusCode()).isEqualTo(403);
        jdbc.update("UPDATE papel_global SET revogado_em=NULL WHERE usuario_id=?",operator.id);

        HttpResponse<String> failed=request(operator,"POST","/privacy-requests/"+requestId+"/purge/execute",null,null);
        assertThat(failed.statusCode()).as(failed.body()).isEqualTo(200);
        assertThat(json.readTree(failed.body()).get("purgeStatus").asText()).isEqualTo("FALHA");
        assertThat(jdbc.queryForObject("SELECT estado FROM execucao_expurgo WHERE id=?",String.class,execution)).isEqualTo("FALHA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expurgo_alvo WHERE execucao_id=? AND estado='FALHA'",Long.class,execution)).isEqualTo(1);
        jdbc.update("UPDATE expurgo_alvo SET referencia_cifrada=? WHERE execucao_id=? AND estado='FALHA'",protector.encrypt("missing/safe.bin"),execution);

        try (var pool=Executors.newFixedThreadPool(2)) {
            Callable<HttpResponse<String>> call=()->request(operator,"POST","/privacy-requests/"+requestId+"/purge/execute",null,null);
            var results=pool.invokeAll(java.util.List.of(call,call));
            assertThat(results).allSatisfy(result->assertThat(result.get().statusCode()).isEqualTo(200));
        }
        JsonNode executed=json.readTree(request(operator,"POST","/privacy-requests/"+requestId+"/purge/execute",null,null).body());
        assertThat(executed.get("status").asText()).isEqualTo("EXPURGADA");
        assertThat(executed.get("verifiedAt").isNull()).isTrue();
        assertThat(executed.get("procedurePending").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(executed.get("preserved").asInt()).isEqualTo(1);

        JsonNode verified=json.readTree(request(operator,"POST","/privacy-requests/"+requestId+"/purge/verify",null,null).body());
        assertThat(verified.get("status").asText()).isEqualTo("VERIFICADA");
        assertThat(verified.get("executedAt").isTextual()).isTrue();
        assertThat(verified.get("verifiedAt").isTextual()).isTrue();
        assertThat(objects.exists(ordinaryKey)).isFalse();
        assertThat(jdbc.queryForObject("SELECT estado FROM documento WHERE id=?",String.class,ordinaryDocument)).isEqualTo("EXPURGADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox WHERE id=?",Long.class,queued)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM posicao_tarefa WHERE id=?",Long.class,financial.position)).isZero();
        assertThat(objects.exists(financialKey)).isTrue();
        assertThat(jdbc.queryForObject("SELECT estado FROM documento WHERE id=?",String.class,financialDocument)).isEqualTo("QUARENTENA");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM operacao_financeira WHERE id=?",Long.class,financial.operation)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM financeiro_outbox WHERE operacao_id=?",Long.class,financial.operation)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pedido_idempotencia WHERE ator_id=? AND operacao='CRIAR'",Long.class,owner.id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM tombstone_expurgo WHERE execucao_id=?",Long.class,execution)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expurgo_alvo WHERE execucao_id=? AND tipo IN ('FORNECEDOR','DISPOSITIVO','BACKUP') AND estado='PROCEDIMENTO_PENDENTE'",Long.class,execution)).isEqualTo(3);
    }

    @Test @Order(1)
    void missingValidatedPolicyAndActiveRetentionExceptionBlockPurge() throws Exception {
        Account owner=account("blocked-owner");
        Account operator=account("blocked-operator");
        Account policyResponsible=account("policy-responsible");
        grantOperator(operator); mfa(operator);
        UUID requestId=UUID.fromString(create(owner,"EXCLUSAO","Solicito exclusão.","blocked").get("id").asText());
        request(operator,"POST","/privacy-requests/"+requestId+"/response",Map.of("status","RESPONDIDA","response","Analisada."),null);
        assertThat(request(operator,"POST","/privacy-requests/"+requestId+"/purge",null,null).statusCode()).isEqualTo(422);
        policy(policyResponsible);
        assertThat(jdbc.queryForObject("SELECT numero FROM politica_retencao WHERE categoria='CONTA' AND desativada_em IS NULL",Integer.class)).isEqualTo(2);
        assertThat(request(operator,"POST","/privacy-requests/"+requestId+"/purge",null,null).statusCode()).isEqualTo(422);
        jdbc.update("UPDATE politica_retencao SET validada_em=clock_timestamp() WHERE categoria='CONTA' AND numero=2");
        jdbc.update("UPDATE usuario SET estado='BLOQUEADO' WHERE id=?",policyResponsible.id);
        assertThat(request(operator,"POST","/privacy-requests/"+requestId+"/purge",null,null).statusCode()).isEqualTo(422);
        jdbc.update("UPDATE usuario SET estado='ATIVO' WHERE id=?",policyResponsible.id);
        jdbc.update("INSERT INTO retencao_excecao(id,categoria,referencia_hash,fundamento,escopo,responsavel_id,revisao_em) VALUES (?,'CONTA',?,'Obrigação específica em validação','Somente referência necessária',?,current_date+1)",UUID.randomUUID(),protector.lookup(owner.id.toString()),operator.id);
        assertThat(request(operator,"POST","/privacy-requests/"+requestId+"/purge",null,null).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM papel_global WHERE usuario_id=?",Long.class,owner.id)).isZero();
    }

    private JsonNode create(Account account,String type,String description,String key) throws Exception {
        HttpResponse<String> response=request(account,"POST","/me/privacy-requests",Map.of("type",type,"description",description),key);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(202);
        return json.readTree(response.body());
    }
    private void policy(Account operator) {
        UUID previous=UUID.randomUUID();
        jdbc.update("INSERT INTO politica_retencao(id,categoria,numero,finalidade_codigo,gatilho,prazo_dias,backup_prazo_dias,responsavel_id,base_validada,verificacao_descarte,validada_em) VALUES (?,'CONTA',1,'ATENDER_EXCLUSAO_TITULAR','SOLICITACAO_RESPONDIDA',0,NULL,?,true,true,clock_timestamp()-interval '1 second')",previous,operator.id);
        jdbc.update("UPDATE politica_retencao SET desativada_em=clock_timestamp() WHERE id=?",previous);
        jdbc.update("INSERT INTO politica_retencao(id,categoria,numero,finalidade_codigo,gatilho,prazo_dias,backup_prazo_dias,responsavel_id,base_validada,verificacao_descarte,validada_em) VALUES (?,'CONTA',2,'ATENDER_EXCLUSAO_TITULAR','SOLICITACAO_RESPONDIDA',0,NULL,?,true,true,clock_timestamp()+interval '1 day')",UUID.randomUUID(),operator.id);
    }
    private UUID document(Account owner,String category,String name) {
        UUID id=UUID.randomUUID();
        byte[] content=("synthetic-"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String key=objects.put(id,new MockMultipartFile("file",name,"application/pdf",content));
        jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?,?,?,decode(md5(?),'hex'),'application/pdf',?,'QUARENTENA')",id,owner.id,category,key,id.toString(),content.length);
        return id;
    }
    private FinancialFixture financialFixture(Account owner,Account driverUser) {
        UUID patient=UUID.randomUUID(); String cpf=String.format("%011d",Math.abs(patient.hashCode()));
        jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,DATE '1980-01-01','VERIFICADA')",patient,owner.id,protector.encrypt(cpf),protector.lookup(cpf));
        UUID deliverer=UUID.randomUUID(),vehicle=UUID.randomUUID(),link=UUID.randomUUID();
        jdbc.update("INSERT INTO entregador(id,usuario_id,nascimento,estado) VALUES (?,?,DATE '1990-01-01','APROVADO')",deliverer,driverUser.id);
        jdbc.update("INSERT INTO veiculo(id,placa,marca,modelo,cor,ano_fabricacao,ano_modelo,estado) VALUES (?,?,'Marca','Modelo','Cor',2024,2024,'APROVADO')",vehicle,"P"+UUID.randomUUID().toString().substring(0,7));
        jdbc.update("INSERT INTO vinculo_veiculo(id,entregador_id,veiculo_id,tipo,estado,valido_ate,version) VALUES (?,?,?,'PROPRIEDADE','APROVADO',current_date+10,1)",link,deliverer,vehicle);
        int number=Math.abs(UUID.randomUUID().hashCode()%1_000_000)+1;
        UUID tariff=UUID.randomUUID(),cancel=UUID.randomUUID(),assignmentPolicy=UUID.randomUUID();
        jdbc.update("INSERT INTO tarifa(id,numero,formula_codigo,parametros,inicio,estado) VALUES (?,?,'TEST','{}',clock_timestamp(),'ATIVA')",tariff,number);
        jdbc.update("INSERT INTO politica_cancelamento(id,numero,criterios,inicio,estado) VALUES (?,?,'{}',clock_timestamp(),'ATIVA')",cancel,number+1);
        jdbc.update("INSERT INTO politica_designacao(id,numero,limite_tarefas_simultaneas,inicio,estado) VALUES (?,?,1,clock_timestamp(),'ATIVA')",assignmentPolicy,number+2);
        UUID order=UUID.randomUUID(),quote=UUID.randomUUID(),assignment=UUID.randomUUID(); byte[] address=protector.encrypt("isolated address");
        jdbc.update("INSERT INTO pedido(id,paciente_id,solicitante_id,origem_cifrada,destino_cifrada,origem_lat,origem_lon,destino_lat,destino_lon,destinatario_id,unidade_id,estado,version) VALUES (?,?,?,?,?,-23,-46,-23,-46,?,?,'EM_ENTREGA',2)",order,patient,owner.id,address,address,owner.id,UUID.randomUUID());
        jdbc.update("INSERT INTO orcamento(id,pedido_id,paciente_id,tarifa_id,rota_provedor,rota_referencia,distancia_m,duracao_s,rota_calculada_em,transito_incluido,politica_snapshot,frete,paciente_valor,subsidio_valor,moeda,expira_em,aceito_em,estado,politica_cancelamento_id,pedido_version) VALUES (?,?,?,?,'test','route',100,60,clock_timestamp(),false,'{}',10,10,0,'BRL',clock_timestamp()+interval '1 hour',clock_timestamp(),'ACEITO',?,1)",quote,order,patient,tariff,cancel);
        jdbc.update("INSERT INTO designacao(id,pedido_id,entregador_id,vinculo_id,orcamento_id,politica_designacao_id,politica_cancelamento_id,pedido_version,limite_tarefas_snapshot,identificacao_snapshot) VALUES (?,?,?,?,?,?,?,2,1,'{}')",assignment,order,deliverer,link,quote,assignmentPolicy,cancel);
        UUID position=UUID.randomUUID();
        jdbc.update("INSERT INTO posicao_tarefa(id,pedido_id,designacao_id,sequencia,capturada_em,latitude,longitude,precisao_m) VALUES (?,?,?,1,clock_timestamp(),-23,-46,10)",position,order,assignment);
        jdbc.update("INSERT INTO pedido_idempotencia(ator_id,operacao,chave,request_hash,pedido_id) VALUES (?,'CRIAR',?,decode(md5(?),'hex'),?)",owner.id,"key-"+order,order.toString(),order);
        UUID operation=UUID.randomUUID();
        jdbc.update("INSERT INTO operacao_financeira(id,pedido_id,tipo,chave_negocio,valor,moeda,estado,provedor,beneficiario_referencia) VALUES (?,?,'COBRANCA',?,10,'BRL','PENDENTE','controlled-test',?)",operation,order,"charge:"+order,owner.id.toString());
        jdbc.update("INSERT INTO financeiro_outbox(id,operacao_id,pedido_id,tipo,chave,payload_saneado,estado) VALUES (?,?,?,'CRIAR_COBRANCA',?,jsonb_build_object('operationId',?::text),'PENDENTE')",UUID.randomUUID(),operation,order,"charge:"+order,operation);
        return new FinancialFixture(position,operation);
    }
    private void grantOperator(Account account) { jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES (?,'ANALISTA_OPERACIONAL')",account.id); }
    private void linkFamilyWithoutPrivacyScope(Account owner,Account family) {
        UUID patient=UUID.randomUUID();
        String cpf=String.format("%011d",Integer.toUnsignedLong(patient.hashCode()));
        jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,DATE '1980-01-01','VERIFICADA')",patient,owner.id,protector.encrypt(cpf),protector.lookup(cpf));
        UUID invitation=UUID.randomUUID();
        jdbc.update("INSERT INTO convite_familiar(id,paciente_id,destinatario_email_busca,token_hash,estado,expira_em,consumido_em,aceito_por,aceito_em,confirmado_em) VALUES (?,?,?,?, 'CONFIRMADO',clock_timestamp()+interval '1 day',clock_timestamp(),?,clock_timestamp(),clock_timestamp())",invitation,patient,protector.lookup("family-"+family.id),protector.lookup("token-"+invitation),family.id);
        jdbc.update("INSERT INTO convite_familiar_escopo(convite_id,escopo) VALUES (?,'PEDIDOS')",invitation);
        UUID grant=UUID.randomUUID();
        jdbc.update("INSERT INTO autorizacao_paciente(id,paciente_id,familiar_id,concedida_por,convite_id,confirmada_em,expira_em) VALUES (?,?,?,?,?,clock_timestamp(),clock_timestamp()+interval '1 day')",grant,patient,family.id,owner.id,invitation);
        jdbc.update("INSERT INTO autorizacao_escopo(autorizacao_id,escopo) VALUES (?,'PEDIDOS')",grant);
    }
    private void mfa(Account account) throws Exception {
        JsonNode enrollment=json.readTree(request(account,"POST","/me/mfa/totp/enrollment",Map.of("password",password),null).body());
        String code=MfaService.totp(MfaService.decodeBase32(enrollment.get("secret").asText()),Instant.now().getEpochSecond()/30);
        assertThat(request(account,"POST","/me/mfa/totp/confirmation",Map.of("code",code),null).statusCode()).isEqualTo(200);
    }
    private Account account(String prefix) throws Exception {
        UUID id=UUID.randomUUID(); String email=prefix+id+"@example.test";
        jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",id,protector.encrypt(email),protector.lookup(email),Passwords.hash(password),protector.encrypt(prefix));
        JsonNode tokens=json.readTree(requestRaw(null,"POST","/auth/login",Map.of("email",email,"password",password,"client","MOBILE"),null).body());
        String token=tokens.get("accessToken").asText();
        UUID session=jdbc.queryForObject("SELECT id FROM sessao WHERE usuario_id=? AND revogada_em IS NULL",UUID.class,id);
        return new Account(id,session,token);
    }
    private HttpResponse<String> request(Account account,String method,String path,Object body,String key) throws Exception { return requestRaw(account,method,path,body,key); }
    private HttpResponse<String> requestRaw(Account account,String method,String path,Object body,String key) throws Exception {
        HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).header("Content-Type","application/json");
        if (account!=null) builder.header("Authorization","Bearer "+account.token);
        if (key!=null) builder.header("Idempotency-Key",key);
        String serialized=body==null?null:json.writeValueAsString(body);
        return http.send(builder.method(method,serialized==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(serialized)).build(),HttpResponse.BodyHandlers.ofString());
    }
    record Account(UUID id,UUID sessionId,String token) {}
    record FinancialFixture(UUID position,UUID operation) {}
}
