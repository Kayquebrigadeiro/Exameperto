package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(properties={
    "security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
    "security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=",
    "security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=",
    "privacy.purge-enabled=true",
    "email.enabled=false"
})
class BackupRestoreFlowTest {
    @Container static final PostgreSQLContainer<?> SOURCE=new PostgreSQLContainer<>("postgres:17.6");
    @Container static final PostgreSQLContainer<?> RESTORE=new PostgreSQLContainer<>("postgres:17.6");
    @Container static final PostgreSQLContainer<?> JOURNAL=new PostgreSQLContainer<>("postgres:17.6");
    static final Path OBJECTS=createDirectory("backup-source-objects-");
    static final Path WORK=createDirectory("exame-perto-backup-restore-");

    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",SOURCE::getJdbcUrl);
        registry.add("spring.datasource.username",SOURCE::getUsername);
        registry.add("spring.datasource.password",SOURCE::getPassword);
        registry.add("storage.private-root",OBJECTS::toString);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DataProtector protector;
    @Autowired PrivateObjectStore objects;
    @Autowired PrivacyService privacy;
    @Autowired AccountMailService mailService;
    @Autowired RecoveryAccessFilter recoveryFilter;

    @Test
    void oldCopyReappliesVerifiedPurgeBeforeReleaseAndPreservesFinancialIdentity() throws Exception {
        UUID owner=user("owner"), operator=user("operator"), driverUser=user("driver");
        UUID session=UUID.randomUUID();
        jdbc.update("INSERT INTO sessao(id,usuario_id,familia_id,refresh_hash,expira_em,cliente,mfa_verificada_ate) VALUES (?,?,?,?,clock_timestamp()+interval '1 hour','WEB',clock_timestamp()+interval '10 minutes')",
            session,operator,UUID.randomUUID(),bytes("refresh"));
        jdbc.update("INSERT INTO papel_global(usuario_id,papel) VALUES (?,'ANALISTA_OPERACIONAL')",operator);
        UUID ordinary=document(owner,"IDENTIDADE","ordinary.bin");
        UUID financialDocument=document(owner,"FINANCIAMENTO","financial.bin");
        Fixture fixture=financialFixture(owner,driverUser);
        UUID mail=UUID.randomUUID();
        jdbc.update("INSERT INTO outbox(id,tipo,chave,payload_saneado,estado,disponivel_em) VALUES (?,'EMAIL',?,jsonb_build_object('usuarioId',?::text),'PENDENTE',clock_timestamp())",mail,"restore:"+mail,owner.toString());
        UUID policy=UUID.randomUUID(), request=UUID.randomUUID();
        jdbc.update("INSERT INTO politica_retencao(id,categoria,numero,finalidade_codigo,gatilho,prazo_dias,responsavel_id,base_validada,verificacao_descarte,validada_em) VALUES (?,'CONTA',901,'ATENDER_EXCLUSAO_TITULAR','SOLICITACAO_RESPONDIDA',0,?,true,true,clock_timestamp())",policy,operator);
        jdbc.update("INSERT INTO solicitacao_privacidade(id,protocolo,usuario_id,request_hash,tipo,estado,responsavel_id,respondida_em) VALUES (?,?,?,?,'EXCLUSAO','RESPONDIDA',?,clock_timestamp())",request,"PRV-RESTORE-"+request.toString().substring(0,8),owner,bytes("request"),operator);
        AuthService.SessionPrincipal principal=new AuthService.SessionPrincipal(operator,session,"WEB");
        privacy.authorizePurge(principal,request);

        Path bundle=WORK.resolve("bundle"), restoredObjects=WORK.resolve("restored-objects");
        Result backup=run("ops/recovery/backup.sh",Map.of(
            "BACKUP_DATABASE_URL",url(SOURCE),"PRIVATE_OBJECT_ROOT",OBJECTS.toString(),
            "BACKUP_DESTINATION",bundle.toString(),"BACKUP_WRITES_QUIESCED","true","APPLICATION_RELEASE","test-a8fbdc2"));
        assertThat(backup.exit).as(backup.output).isZero();

        assertThat(privacy.execute(principal,request).status()).isEqualTo("EXPURGADA");
        assertThat(privacy.verify(principal,request).status()).isEqualTo("VERIFICADA");
        Result capture=run("ops/recovery/capture-purges.sh",Map.of(
            "SOURCE_DATABASE_URL",url(SOURCE),"PURGE_JOURNAL_DATABASE_URL",url(JOURNAL)));
        assertThat(capture.exit).as(capture.output).isZero();

        Map<String,String> restoreEnv=Map.of(
            "RESTORE_DATABASE_URL",url(RESTORE),"RESTORE_PRIVATE_OBJECT_ROOT",restoredObjects.toString(),
            "RESTORE_BUNDLE",bundle.toString(),"PURGE_JOURNAL_DATABASE_URL",url(JOURNAL),
            "RESTORE_ISOLATION_CONFIRMED","true","EXTERNAL_EFFECTS_DISABLED","true");
        var interrupted=new java.util.HashMap<>(restoreEnv); interrupted.put("RECOVERY_FAIL_AFTER_RESTORE","true");
        Result first=run("ops/recovery/restore.sh",interrupted);
        assertThat(first.exit).isNotZero();
        assertThat(first.output).contains("interrupção de ensaio");
        JdbcTemplate restored=jdbc(RESTORE);
        assertThat(restored.queryForObject("SELECT estado FROM controle_restauracao",String.class)).isEqualTo("BLOQUEADO");

        Result resumed=run("ops/recovery/restore.sh",restoreEnv);
        assertThat(resumed.exit).as(resumed.output).isZero();
        Result repeated=run("ops/recovery/restore.sh",restoreEnv);
        assertThat(repeated.exit).as(repeated.output).isZero();
        assertThat(restored.queryForObject("SELECT estado FROM controle_restauracao",String.class)).isEqualTo("VERIFICADO");
        assertThat(restored.queryForObject("SELECT estado FROM documento WHERE id=?",String.class,ordinary)).isEqualTo("EXPURGADO");
        assertThat(Files.exists(restoredObjects.resolve("quarantine").resolve(ordinary+".bin"))).isFalse();
        assertThat(restored.queryForObject("SELECT count(*) FROM posicao_tarefa WHERE id=?",Long.class,fixture.position)).isZero();
        assertThat(restored.queryForObject("SELECT count(*) FROM outbox WHERE id=?",Long.class,mail)).isZero();
        assertThat(restored.queryForObject("SELECT estado FROM documento WHERE id=?",String.class,financialDocument)).isEqualTo("QUARENTENA");
        assertThat(Files.exists(restoredObjects.resolve("quarantine").resolve(financialDocument+".bin"))).isTrue();
        assertThat(restored.queryForObject("SELECT valor FROM operacao_financeira WHERE id=?",java.math.BigDecimal.class,fixture.operation)).isEqualByComparingTo("10.00");
        assertThat(restored.queryForObject("SELECT chave_negocio FROM operacao_financeira WHERE id=?",String.class,fixture.operation)).isEqualTo("charge:"+fixture.order);
        assertThat(restored.queryForObject("SELECT count(*) FROM financeiro_outbox WHERE operacao_id=?",Long.class,fixture.operation)).isOne();
        assertThat(restored.queryForObject("SELECT count(*) FROM pedido_idempotencia WHERE ator_id=? AND operacao='CRIAR'",Long.class,owner)).isOne();
        assertThat(restored.queryForObject("SELECT count(*) FROM restauracao_expurgo_aplicado",Long.class)).isOne();

        jdbc.update("UPDATE controle_restauracao SET estado='BLOQUEADO',backup_id=? WHERE singleton",UUID.randomUUID());
        MockHttpServletResponse blocked=new MockHttpServletResponse();
        recoveryFilter.doFilter(new MockHttpServletRequest("GET","/api/v1/me/privacy-requests/"+UUID.randomUUID()),blocked,new MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(503);
        assertThat(blocked.getContentAsString()).contains("RESTORE_BLOCKED");
        assertThatThrownBy(()->mailService.deliver(UUID.randomUUID())).hasMessageContaining("Efeitos externos bloqueados");
        jdbc.update("UPDATE controle_restauracao SET estado='NORMAL' WHERE singleton");

        jdbc.update("DELETE FROM controle_restauracao WHERE singleton");
        MockHttpServletResponse missingGate=new MockHttpServletResponse();
        recoveryFilter.doFilter(new MockHttpServletRequest("GET","/api/v1/me"),missingGate,new MockFilterChain());
        assertThat(missingGate.getStatus()).isEqualTo(503);
        assertThatThrownBy(()->mailService.deliver(UUID.randomUUID())).hasMessageContaining("Efeitos externos bloqueados");
        jdbc.update("INSERT INTO controle_restauracao(singleton,estado) VALUES (true,'NORMAL')");

        Result release=run("ops/recovery/release-access.sh",Map.of("RESTORE_DATABASE_URL",url(RESTORE),"RESTORE_RELEASE_APPROVED","true"));
        assertThat(release.exit).as(release.output).isZero();
        assertThat(restored.queryForObject("SELECT estado FROM controle_restauracao",String.class)).isEqualTo("NORMAL");

        Path corrupt=WORK.resolve("corrupt"); copy(bundle,corrupt);
        Files.write(corrupt.resolve("database.dump"),new byte[]{1},StandardOpenOption.APPEND);
        var corruptEnv=new java.util.HashMap<>(restoreEnv); corruptEnv.put("RESTORE_BUNDLE",corrupt.toString());
        Result rejected=run("ops/recovery/restore.sh",corruptEnv);
        assertThat(rejected.exit).isNotZero();
        assertThat(rejected.output).contains("integridade");
    }

    private UUID user(String label) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",
            id,protector.encrypt(label+id+"@example.test"),protector.lookup(label+id+"@example.test"),Passwords.hash("Synthetic-123"),protector.encrypt(label));
        return id;
    }

    private UUID document(UUID owner,String category,String name) {
        UUID id=UUID.randomUUID(); byte[] content=bytes("synthetic-"+name);
        String key=objects.put(id,new MockMultipartFile("file",name,"application/pdf",content));
        jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?,?,?,decode(md5(?),'hex'),'application/pdf',?,'QUARENTENA')",
            id,owner,category,key,id.toString(),content.length);
        return id;
    }

    private Fixture financialFixture(UUID owner,UUID driverUser) {
        UUID patient=UUID.randomUUID();
        jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,DATE '1980-01-01','VERIFICADA')",patient,owner,protector.encrypt(patient.toString()),protector.lookup(patient.toString()));
        UUID deliverer=UUID.randomUUID(),vehicle=UUID.randomUUID(),link=UUID.randomUUID();
        jdbc.update("INSERT INTO entregador(id,usuario_id,nascimento,estado) VALUES (?,?,DATE '1990-01-01','APROVADO')",deliverer,driverUser);
        jdbc.update("INSERT INTO veiculo(id,placa,marca,modelo,cor,ano_fabricacao,ano_modelo,estado) VALUES (?,?,'M','X','C',2024,2024,'APROVADO')",vehicle,"T"+vehicle.toString().substring(0,7));
        jdbc.update("INSERT INTO vinculo_veiculo(id,entregador_id,veiculo_id,tipo,estado,valido_ate,version) VALUES (?,?,?,'PROPRIEDADE','APROVADO',current_date+10,1)",link,deliverer,vehicle);
        int n=Math.abs(UUID.randomUUID().hashCode()%1000000)+1000;
        UUID tariff=UUID.randomUUID(),cancel=UUID.randomUUID(),assignmentPolicy=UUID.randomUUID();
        jdbc.update("INSERT INTO tarifa(id,numero,formula_codigo,parametros,inicio,estado) VALUES (?,?,'TEST','{}',clock_timestamp(),'ATIVA')",tariff,n);
        jdbc.update("INSERT INTO politica_cancelamento(id,numero,criterios,inicio,estado) VALUES (?,?,'{}',clock_timestamp(),'ATIVA')",cancel,n+1);
        jdbc.update("INSERT INTO politica_designacao(id,numero,limite_tarefas_simultaneas,inicio,estado) VALUES (?,?,1,clock_timestamp(),'ATIVA')",assignmentPolicy,n+2);
        UUID order=UUID.randomUUID(),quote=UUID.randomUUID(),assignment=UUID.randomUUID(); byte[] address=protector.encrypt("synthetic address");
        jdbc.update("INSERT INTO pedido(id,paciente_id,solicitante_id,origem_cifrada,destino_cifrada,origem_lat,origem_lon,destino_lat,destino_lon,destinatario_id,unidade_id,estado,version) VALUES (?,?,?,?,?,-23,-46,-23,-46,?,?,'EM_ENTREGA',2)",order,patient,owner,address,address,owner,UUID.randomUUID());
        jdbc.update("INSERT INTO orcamento(id,pedido_id,paciente_id,tarifa_id,rota_provedor,rota_referencia,distancia_m,duracao_s,rota_calculada_em,transito_incluido,politica_snapshot,frete,paciente_valor,subsidio_valor,moeda,expira_em,aceito_em,estado,politica_cancelamento_id,pedido_version) VALUES (?,?,?,?,'test','route',100,60,clock_timestamp(),false,'{}',10,10,0,'BRL',clock_timestamp()+interval '1 hour',clock_timestamp(),'ACEITO',?,1)",quote,order,patient,tariff,cancel);
        jdbc.update("INSERT INTO designacao(id,pedido_id,entregador_id,vinculo_id,orcamento_id,politica_designacao_id,politica_cancelamento_id,pedido_version,limite_tarefas_snapshot,identificacao_snapshot) VALUES (?,?,?,?,?,?,?,2,1,'{}')",assignment,order,deliverer,link,quote,assignmentPolicy,cancel);
        UUID position=UUID.randomUUID();
        jdbc.update("INSERT INTO posicao_tarefa(id,pedido_id,designacao_id,sequencia,capturada_em,latitude,longitude,precisao_m) VALUES (?,?,?,1,clock_timestamp(),-23,-46,10)",position,order,assignment);
        jdbc.update("INSERT INTO pedido_idempotencia(ator_id,operacao,chave,request_hash,pedido_id) VALUES (?,'CRIAR',?,decode(md5(?),'hex'),?)",owner,"key:"+order,order.toString(),order);
        UUID operation=UUID.randomUUID();
        jdbc.update("INSERT INTO operacao_financeira(id,pedido_id,tipo,chave_negocio,valor,moeda,estado,provedor,beneficiario_referencia) VALUES (?,?,'COBRANCA',?,10,'BRL','PENDENTE','controlled-test',?)",operation,order,"charge:"+order,owner.toString());
        jdbc.update("INSERT INTO financeiro_outbox(id,operacao_id,pedido_id,tipo,chave,payload_saneado,estado) VALUES (?,?,?,'CRIAR_COBRANCA',?,jsonb_build_object('operationId',?::text),'PENDENTE')",UUID.randomUUID(),operation,order,"charge:"+order,operation);
        return new Fixture(position,operation,order);
    }

    private static Result run(String script,Map<String,String> env) throws Exception {
        Path root=Path.of(System.getProperty("user.dir")).toAbsolutePath();
        if (!Files.isDirectory(root.resolve("ops"))) root=root.getParent();
        ProcessBuilder builder=new ProcessBuilder("bash",root.resolve(script).toString()).redirectErrorStream(true);
        builder.directory(root.toFile()); builder.environment().putAll(env);
        Process process=builder.start(); String output=new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(90,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
        return new Result(process.exitValue(),output);
    }
    private static String url(PostgreSQLContainer<?> db) {
        return "postgresql://"+db.getUsername()+":"+db.getPassword()+"@"+db.getHost()+":"+db.getFirstMappedPort()+"/"+db.getDatabaseName();
    }
    private static JdbcTemplate jdbc(PostgreSQLContainer<?> db) { return new JdbcTemplate(new DriverManagerDataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword())); }
    private static byte[] bytes(String value) { return value.getBytes(java.nio.charset.StandardCharsets.UTF_8); }
    private static Path createDirectory(String prefix) { try { return Files.createTempDirectory(prefix); } catch(Exception ex) { throw new IllegalStateException(ex); } }
    @AfterAll static void cleanup() throws Exception { deleteTree(WORK); deleteTree(OBJECTS); }
    private static void deleteTree(Path root) throws Exception {
        if (!Files.exists(root)) return;
        try(var paths=Files.walk(root)) {
            for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }
    private static void copy(Path source,Path target) throws Exception {
        try(var paths=Files.walk(source)) { for(Path path:paths.toList()) { Path out=target.resolve(source.relativize(path).toString()); if(Files.isDirectory(path)) Files.createDirectories(out); else Files.copy(path,out); } }
    }
    record Fixture(UUID position,UUID operation,UUID order) {}
    record Result(int exit,String output) {}
}
