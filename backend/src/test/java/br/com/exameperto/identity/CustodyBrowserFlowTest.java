package br.com.exameperto.identity;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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

@org.springframework.test.annotation.DirtiesContext
@Testcontainers
@EnabledIfSystemProperty(named="browserTest",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"registration.privacy-approved=true","registration.allowed-origin=http://127.0.0.1:5187","security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=","security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI=","registration.attempts-per-minute=1000","registration.global-attempts-per-minute=5000"})
class CustodyBrowserFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);}
    @LocalServerPort int port; @Autowired JdbcTemplate jdbc; @Autowired DataProtector protector;

    @Test void browserCompletesRealCustodyFlowAndKeepsIncidentOpen() throws Exception {
        Account recipient=account("custody-recipient"),driverAccount=account("custody-driver"),outsider=account("custody-outsider");
        UUID patient=patient(recipient),driver=UUID.randomUUID(),vehicle=UUID.randomUUID(),link=UUID.randomUUID(),photo=document(driverAccount.id,"FOTO_OPERACIONAL","INSPECAO_APROVADA"),evidence=document(driverAccount.id,"COMPROVANTE","INSPECAO_APROVADA");
        jdbc.update("INSERT INTO entregador(id,usuario_id,nascimento,estado,foto_aprovada_id) VALUES (?,?,DATE '1990-01-01','APROVADO',?)",driver,driverAccount.id,photo);
        jdbc.update("INSERT INTO veiculo(id,placa,marca,modelo,cor,ano_fabricacao,ano_modelo,estado) VALUES (?,'CST1A23','Marca','Modelo','Azul',2024,2025,'APROVADO')",vehicle);
        jdbc.update("INSERT INTO vinculo_veiculo(id,entregador_id,veiculo_id,tipo,estado,valido_ate,version) VALUES (?,?,?,'PROPRIEDADE','APROVADO',current_date+10,1)",link,driver,vehicle);
        UUID tariff=UUID.randomUUID(),cancel=UUID.randomUUID(),assignmentPolicy=UUID.randomUUID(),unit=UUID.randomUUID(),protocol=UUID.randomUUID();
        jdbc.update("INSERT INTO tarifa(id,numero,formula_codigo,parametros,inicio,estado) VALUES (?,101,'BASE_KM_MINUTO','{}',clock_timestamp(),'ATIVA')",tariff);
        jdbc.update("INSERT INTO politica_cancelamento(id,numero,criterios,inicio,estado) VALUES (?,102,'{}',clock_timestamp(),'ATIVA')",cancel);
        jdbc.update("INSERT INTO politica_designacao(id,numero,limite_tarefas_simultaneas,inicio,estado) VALUES (?,103,3,clock_timestamp(),'ATIVA')",assignmentPolicy);
        jdbc.update("INSERT INTO protocolo_custodia(id,unidade_id,numero,regras,cobertura_retorno_confirmada,inicio,estado) VALUES (?,?,1,'{}',true,clock_timestamp(),'HABILITADO')",protocol,unit);
        UUID delivery=order(patient,recipient.id,driver,link,tariff,cancel,assignmentPolicy,unit,protocol);
        UUID incident=order(patient,recipient.id,driver,link,tariff,cancel,assignmentPolicy,unit,protocol);

        ProcessBuilder builder=new ProcessBuilder("npm","run","test","--","tests/custody-real.spec.ts");
        builder.directory(Path.of("..","web").toFile()); builder.redirectErrorStream(true); builder.redirectOutput(Path.of("target","custody-browser-playwright.log").toFile());
        builder.environment().put("API_TARGET","http://127.0.0.1:"+port); builder.environment().put("CUSTODY_RECIPIENT_EMAIL",recipient.email); builder.environment().put("CUSTODY_DRIVER_EMAIL",driverAccount.email); builder.environment().put("CUSTODY_OUTSIDER_EMAIL",outsider.email); builder.environment().put("CUSTODY_DELIVERY_ORDER_ID",delivery.toString()); builder.environment().put("CUSTODY_INCIDENT_ORDER_ID",incident.toString()); builder.environment().put("CUSTODY_EVIDENCE_ID",evidence.toString());
        Process process=builder.start(); assertThat(process.waitFor(120,TimeUnit.SECONDS)).isTrue(); assertThat(process.exitValue()).isZero();
        assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE id=?",String.class,delivery)).isEqualTo("ENTREGUE");
        assertThat(jdbc.queryForObject("SELECT encerrada_em IS NOT NULL FROM custodia WHERE pedido_id=?",Boolean.class,delivery)).isTrue();
        assertThat(jdbc.queryForObject("SELECT estado FROM pedido WHERE id=?",String.class,incident)).isEqualTo("OCORRENCIA");
        assertThat(jdbc.queryForObject("SELECT encerrada_em IS NULL FROM custodia WHERE pedido_id=?",Boolean.class,incident)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM codigo_recebimento WHERE pedido_id=? AND usado_em IS NOT NULL",Long.class,delivery)).isEqualTo(1);
    }

    private UUID order(UUID patient,UUID recipient,UUID driver,UUID link,UUID tariff,UUID cancel,UUID assignmentPolicy,UUID unit,UUID protocol){
        UUID order=UUID.randomUUID(),quote=UUID.randomUUID(),assignment=UUID.randomUUID(),authorizationDocument=document(recipient,"AUTORIZACAO_RETIRADA","QUARENTENA"),authorization=UUID.randomUUID(); byte[] address=protector.encrypt("{\"street\":\"Rua sintética\",\"number\":\"10\",\"district\":\"Centro\",\"city\":\"Cidade\",\"state\":\"SP\",\"postalCode\":\"01001000\",\"latitude\":-23.55,\"longitude\":-46.63}");
        jdbc.update("INSERT INTO pedido(id,paciente_id,solicitante_id,origem_cifrada,destino_cifrada,origem_lat,origem_lon,destino_lat,destino_lon,destinatario_id,unidade_id,protocolo_id,estado,version) VALUES (?,?,?,?,?,-23.55,-46.63,-23.54,-46.62,?,?,?,'ACEITA',3)",order,patient,recipient,address,address,recipient,unit,protocol);
        jdbc.update("INSERT INTO autorizacao_retirada(id,pedido_id,documento_id,estado,criada_por,valida_ate) VALUES (?,?,?,'VERIFICADA',?,clock_timestamp()+interval '1 day')",authorization,order,authorizationDocument,recipient);
        jdbc.update("INSERT INTO orcamento(id,pedido_id,paciente_id,tarifa_id,rota_provedor,rota_referencia,distancia_m,duracao_s,rota_calculada_em,transito_incluido,politica_snapshot,frete,paciente_valor,subsidio_valor,moeda,expira_em,aceito_em,estado,politica_cancelamento_id,pedido_version) VALUES (?,?,?,?,'controlled','custody-browser',1000,300,clock_timestamp(),false,'{}',38,38,0,'BRL',clock_timestamp()+interval '1 hour',clock_timestamp(),'ACEITO',?,2)",quote,order,patient,tariff,cancel);
        jdbc.update("INSERT INTO designacao(id,pedido_id,entregador_id,vinculo_id,orcamento_id,politica_designacao_id,politica_cancelamento_id,pedido_version,limite_tarefas_snapshot,identificacao_snapshot) VALUES (?,?,?,?,?,?,?,?,3,?::jsonb)",assignment,order,driver,link,quote,assignmentPolicy,cancel,2,"{\"displayName\":\"Entregador sintético\",\"plate\":\"CST1A23\",\"model\":\"Modelo\",\"color\":\"Azul\"}"); return order;
    }
    private Account account(String prefix){UUID id=UUID.randomUUID();String email=prefix+"-"+id+"@example.test";jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",id,protector.encrypt(email),protector.lookup(email),Passwords.hash("Senha-sintetica-123"),protector.encrypt(prefix));return new Account(id,email);}
    private UUID patient(Account account){UUID id=UUID.randomUUID();String cpf=String.format("%011d",Integer.toUnsignedLong(id.hashCode()));jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,DATE '1980-01-01','VERIFICADA')",id,account.id,protector.encrypt(cpf),protector.lookup(cpf));return id;}
    private UUID document(UUID owner,String category,String state){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?,?,?,'12345678901234567890123456789012','application/octet-stream',100,?)",id,owner,category,"synthetic/"+id,state);return id;}
    record Account(UUID id,String email){}
}
