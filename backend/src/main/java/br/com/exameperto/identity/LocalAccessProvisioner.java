package br.com.exameperto.identity;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Order(-100)
@ConditionalOnProperty(name="local.provision.enabled",havingValue="true")
class LocalAccessProvisioner implements ApplicationRunner {
    private static final Set<String> GLOBAL=Set.of("ANALISTA_OPERACIONAL","GESTOR_FINANCEIRO","OPERADOR_PRIVACIDADE");
    private static final Set<String> INSTITUTIONAL=Set.of("ANALISTA_BENEFICIO","GESTOR_FINANCEIRO");
    private final JdbcTemplate jdbc;
    private final DataProtector protector;
    private final TransactionTemplate transactions;
    private final ConfigurableApplicationContext context;
    private final String databaseUrl;

    LocalAccessProvisioner(JdbcTemplate jdbc,DataProtector protector,TransactionTemplate transactions,ConfigurableApplicationContext context,@org.springframework.beans.factory.annotation.Value("${spring.datasource.url}") String databaseUrl){this.jdbc=jdbc;this.protector=protector;this.transactions=transactions;this.context=context;this.databaseUrl=databaseUrl;}

    @Override public void run(ApplicationArguments ignored) throws Exception {
        try {
            requireLocalDatabase();
            BufferedReader input=new BufferedReader(new InputStreamReader(System.in));
            System.out.print("E-mail da conta local já cadastrada e confirmada: ");
            String email=input.readLine();
            System.out.print("Ação (conceder/revogar): ");
            String action=normalized(input.readLine());
            System.out.print("Papel (ANALISTA_OPERACIONAL, OPERADOR_PRIVACIDADE, ANALISTA_BENEFICIO ou GESTOR_FINANCEIRO): ");
            String role=normalized(input.readLine());
            if (!Set.of("CONCEDER","REVOGAR").contains(action)||(!GLOBAL.contains(role)&&!INSTITUTIONAL.contains(role))) throw new IllegalArgumentException("Ação ou papel não reconhecido.");
            UUID institution=null;
            if (INSTITUTIONAL.contains(role)) {
                System.out.print("UUID de uma instituição existente (obrigatório para papel institucional): ");
                institution=UUID.fromString(input.readLine().strip());
            }
            UUID institutionId=institution;
            transactions.executeWithoutResult(status->change(email,action,role,institutionId));
            System.out.println("Concessão nominal registrada na auditoria local. Nenhuma identidade, documento, política ou instituição foi aprovada.");
        } finally {
            SpringApplication.exit(context);
        }
    }

    private void change(String email,String action,String role,UUID institution) {
        UUID user=jdbc.queryForObject("SELECT id FROM usuario WHERE email_busca=? AND estado='ATIVO'",UUID.class,protector.lookup(email.strip().toLowerCase(Locale.ROOT)));
        boolean institutional=institution!=null;
        if (institutional&&jdbc.queryForObject("SELECT count(*) FROM instituicao WHERE id=?",Long.class,institution)!=1) throw new IllegalArgumentException("Instituição inexistente; a ferramenta não cria parceiros ou políticas.");
        if (institutional) {
            if ("CONCEDER".equals(action)) jdbc.update("INSERT INTO membro_instituicao(instituicao_id,usuario_id,papel,revogado_em) VALUES (?,?,?,NULL) ON CONFLICT (instituicao_id,usuario_id,papel) DO UPDATE SET revogado_em=NULL",institution,user,role);
            else if (jdbc.update("UPDATE membro_instituicao SET revogado_em=clock_timestamp() WHERE instituicao_id=? AND usuario_id=? AND papel=? AND revogado_em IS NULL",institution,user,role)!=1) throw new IllegalStateException("O vínculo institucional ativo não existe; nenhuma revogação foi registrada.");
            audit(user,institution,role,"INSTITUICAO",action);
        }
        if (!institutional||"GESTOR_FINANCEIRO".equals(role)) {
            if ("CONCEDER".equals(action)) jdbc.update("INSERT INTO papel_global(usuario_id,papel,concedido_por,revogado_em,motivo) VALUES (?,?,?,NULL,'AVALIACAO_LOCAL_NOMINAL') ON CONFLICT (usuario_id,papel) DO UPDATE SET concedido_por=excluded.concedido_por,concedido_em=clock_timestamp(),revogado_em=NULL,motivo=excluded.motivo",user,role,user);
            else if (jdbc.update("UPDATE papel_global SET revogado_em=clock_timestamp(),motivo='AVALIACAO_LOCAL_NOMINAL' WHERE usuario_id=? AND papel=? AND revogado_em IS NULL",user,role)!=1) throw new IllegalStateException("O papel global ativo não existe; nenhuma revogação foi registrada.");
            audit(user,null,role,"GLOBAL",action);
        }
    }

    private void audit(UUID user,UUID institution,String role,String scope,String action){jdbc.update("INSERT INTO auditoria_acesso_local(id,usuario_id,instituicao_id,papel,escopo,acao,motivo) VALUES (?,?,?,?,?,?, 'AVALIACAO_LOCAL_NOMINAL')",UUID.randomUUID(),user,institution,role,scope,"CONCEDER".equals(action)?"CONCEDIDO":"REVOGADO");}

    private void requireLocalDatabase() {
        String value=databaseUrl.startsWith("jdbc:")?databaseUrl.substring(5):databaseUrl;
        String host=URI.create(value).getHost();
        if (!Set.of("127.0.0.1","localhost").contains(host)) throw new IllegalStateException("Provisionamento recusado fora de um PostgreSQL local.");
    }
    private static String normalized(String value){return value==null?"":value.strip().toUpperCase(Locale.ROOT);}
}
