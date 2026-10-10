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
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"registration.privacy-approved=true","registration.allowed-origin=http://127.0.0.1:5187","security.data-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=","security.search-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=","security.signing-key=AgICAgICAgICAgICAgICAgICAgICAgICAgICAgICAgI="})
class AssignmentBrowserFlowTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:17.6");
    @DynamicPropertySource static void db(DynamicPropertyRegistry r){r.add("spring.datasource.url",POSTGRES::getJdbcUrl);r.add("spring.datasource.username",POSTGRES::getUsername);r.add("spring.datasource.password",POSTGRES::getPassword);}
    @LocalServerPort int port;@Autowired JdbcTemplate jdbc;@Autowired DataProtector protector;

    @Test void patientBrowserReadsAssignmentFromApiAndDatabase()throws Exception{
        String email="assignment-browser-"+UUID.randomUUID()+"@example.test";UUID user=UUID.randomUUID(),patient=UUID.randomUUID(),driverUser=UUID.randomUUID(),driver=UUID.randomUUID(),vehicle=UUID.randomUUID(),link=UUID.randomUUID(),order=UUID.randomUUID(),tariff=UUID.randomUUID(),quote=UUID.randomUUID(),policy=UUID.randomUUID(),assignmentPolicy=UUID.randomUUID(),assignment=UUID.randomUUID(),photo=UUID.randomUUID(),authorizationDocument=UUID.randomUUID(),authorization=UUID.randomUUID();
        jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",user,protector.encrypt(email),protector.lookup(email),Passwords.hash("Senha-sintetica-123"),protector.encrypt("Paciente navegador"));
        jdbc.update("INSERT INTO paciente(id,usuario_id,cpf_cifrado,cpf_busca,nascimento,identidade_estado) VALUES (?,?,?,?,DATE '1980-01-01','VERIFICADA')",patient,user,protector.encrypt("12345678901"),protector.lookup("12345678901"));
        jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado,email_verificado_em) VALUES (?,?,?,?,?,'ATIVO',clock_timestamp())",driverUser,protector.encrypt("driver-"+driverUser+"@example.test"),protector.lookup("driver-"+driverUser+"@example.test"),Passwords.hash("Senha-sintetica-123"),protector.encrypt("Entregador navegador"));
        jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?,'FOTO_OPERACIONAL',?,'12345678901234567890123456789012','image/jpeg',100,'INSPECAO_APROVADA')",photo,driverUser,"synthetic/"+photo);
        jdbc.update("INSERT INTO entregador(id,usuario_id,nascimento,estado,foto_aprovada_id) VALUES (?,?,DATE '1990-01-01','APROVADO',?)",driver,driverUser,photo);
        jdbc.update("INSERT INTO veiculo(id,placa,marca,modelo,cor,ano_fabricacao,ano_modelo,estado) VALUES (?,'XYZ1A23','Marca','Modelo seguro','Azul',2024,2025,'APROVADO')",vehicle);
        jdbc.update("INSERT INTO vinculo_veiculo(id,entregador_id,veiculo_id,tipo,estado,valido_ate,version) VALUES (?,?,?,'PROPRIEDADE','APROVADO',current_date+10,1)",link,driver,vehicle);
        byte[] address=protector.encrypt("{\"street\":\"Rua privada\",\"number\":\"10\",\"district\":\"Centro\",\"city\":\"Cidade\",\"state\":\"SP\",\"postalCode\":\"01001000\",\"latitude\":-23.55,\"longitude\":-46.63}");
        jdbc.update("INSERT INTO pedido(id,paciente_id,solicitante_id,origem_cifrada,destino_cifrada,origem_lat,origem_lon,destino_lat,destino_lon,destinatario_id,unidade_id,estado,version) VALUES (?,?,?,?,?,-23.55,-46.63,-22.90,-43.17,?,?,'ACEITA',3)",order,patient,user,address,address,user,UUID.randomUUID());
        jdbc.update("INSERT INTO documento(id,proprietario_id,categoria,objeto_chave,sha256,mime,tamanho,estado) VALUES (?,?,'AUTORIZACAO_RETIRADA',?,'12345678901234567890123456789012','application/pdf',100,'QUARENTENA')",authorizationDocument,user,"synthetic/"+authorizationDocument);
        jdbc.update("INSERT INTO autorizacao_retirada(id,pedido_id,documento_id,estado,criada_por,valida_ate) VALUES (?,?,?,'VERIFICADA',?,clock_timestamp()+interval '1 day')",authorization,order,authorizationDocument,user);
        jdbc.update("INSERT INTO tarifa(id,numero,formula_codigo,parametros,inicio,estado) VALUES (?,1,'BASE_KM_MINUTO','{}',clock_timestamp(),'ATIVA')",tariff);
        jdbc.update("INSERT INTO politica_cancelamento(id,numero,criterios,inicio,estado) VALUES (?,1,'{}',clock_timestamp(),'ATIVA')",policy);
        jdbc.update("INSERT INTO politica_designacao(id,numero,limite_tarefas_simultaneas,inicio,estado) VALUES (?,1,1,clock_timestamp(),'ATIVA')",assignmentPolicy);
        jdbc.update("INSERT INTO orcamento(id,pedido_id,paciente_id,tarifa_id,rota_provedor,rota_referencia,distancia_m,duracao_s,rota_calculada_em,transito_incluido,politica_snapshot,frete,paciente_valor,subsidio_valor,moeda,expira_em,aceito_em,estado,politica_cancelamento_id,pedido_version) VALUES (?,?,?,?,'controlled','browser',1000,300,clock_timestamp(),false,'{}',38,38,0,'BRL',clock_timestamp()+interval '1 hour',clock_timestamp(),'ACEITO',?,2)",quote,order,patient,tariff,policy);
        jdbc.update("INSERT INTO designacao(id,pedido_id,entregador_id,vinculo_id,orcamento_id,politica_designacao_id,politica_cancelamento_id,pedido_version,limite_tarefas_snapshot,identificacao_snapshot) VALUES (?,?,?,?,?,?,?,?,1,?::jsonb)",assignment,order,driver,link,quote,assignmentPolicy,policy,2,"{\"displayName\":\"Entregador navegador\",\"plate\":\"XYZ1A23\",\"model\":\"Modelo seguro\",\"color\":\"Azul\",\"photoDocumentId\":\""+photo+"\"}");
        var builder=new ProcessBuilder("npm","run","test","--","tests/assignment-real.spec.ts");builder.directory(Path.of("..","web").toFile());builder.redirectErrorStream(true);builder.redirectOutput(Path.of("target","assignment-browser-playwright.log").toFile());builder.environment().put("API_TARGET","http://127.0.0.1:"+port);builder.environment().put("VITE_ENABLE_OPERATIONAL_TEST_PANELS","true");builder.environment().put("ASSIGNMENT_EMAIL",email);builder.environment().put("ASSIGNMENT_ORDER_ID",order.toString());Process process=builder.start();assertThat(process.waitFor(120,TimeUnit.SECONDS)).isTrue();assertThat(process.exitValue()).isZero();assertThat(jdbc.queryForObject("SELECT count(*) FROM designacao WHERE pedido_id=? AND encerrada_em IS NULL",Long.class,order)).isEqualTo(1);
    }
}
