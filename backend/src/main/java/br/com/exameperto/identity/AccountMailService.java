package br.com.exameperto.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Queue contains identifiers only. A 202 never asserts that a message was sent. */
@Service
public class AccountMailService {
    private final JdbcTemplate jdbc;
    private final DataProtector protector;
    private final EmailGateway email;
    private final Duration resendWindow, emailTtl, recoveryTtl;
    private final boolean privacyApproved;
    private final RecoveryGuard recovery;
    AccountMailService(JdbcTemplate jdbc, DataProtector protector, EmailGateway email,
        @Value("${registration.email-resend-window:PT1M}") Duration resendWindow,
        @Value("${registration.email-token-ttl:PT30M}") Duration emailTtl,
        @Value("${registration.recovery-token-ttl:PT30M}") Duration recoveryTtl,
        @Value("${registration.privacy-approved:false}") boolean privacyApproved, RecoveryGuard recovery) {
        this.jdbc=jdbc; this.protector=protector; this.email=email; this.resendWindow=resendWindow;
        this.emailTtl=emailTtl; this.recoveryTtl=recoveryTtl; this.privacyApproved=privacyApproved; this.recovery=recovery;
    }
    @Transactional
    public void request(String address, String type) {
        if (!privacyApproved) throw new AuthService.AuthException(org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,"POLICY_UNDEFINED","A política de privacidade aguarda validação.");
        if (!email.configured() || !protector.configured()) throw AuthService.unavailable();
        var users=jdbc.queryForList("SELECT id,estado FROM usuario WHERE email_busca=? FOR UPDATE", protector.lookup(AuthService.normalize(address)));
        if (users.isEmpty()) return;
        var user=users.getFirst();
        if (!(type.equals("EMAIL") ? "PENDENTE_EMAIL" : "ATIVO").equals(user.get("estado"))) return;
        UUID id=(UUID)user.get("id");
        if (jdbc.queryForObject("SELECT count(*) FROM desafio_conta WHERE usuario_id=? AND tipo=? AND created_at>?", Long.class,id,type,java.sql.Timestamp.from(Instant.now().minus(resendWindow))) > 0) return;
        String key=type+":"+id;
        jdbc.update("INSERT INTO outbox(id,tipo,chave,payload_saneado,estado,disponivel_em) VALUES (?,?,?,jsonb_build_object('usuarioId',cast(? as text)),'PENDENTE',clock_timestamp()) ON CONFLICT(chave) DO UPDATE SET estado='PENDENTE',disponivel_em=clock_timestamp(),updated_at=clock_timestamp() WHERE outbox.estado IN ('ENVIADO','RECONCILIAR') AND outbox.updated_at<?", UUID.randomUUID(),type,key,id.toString(),java.sql.Timestamp.from(Instant.now().minus(resendWindow)));
    }

    @Transactional
    public void deliver(UUID outboxId) {
        recovery.requireExternalEffectsAllowed();
        var jobs=jdbc.queryForList("SELECT * FROM outbox WHERE id=? AND estado='PENDENTE' FOR UPDATE SKIP LOCKED",outboxId);
        if (jobs.isEmpty()) return;
        var job=jobs.getFirst();
        UUID userId=UUID.fromString(jdbc.queryForObject("SELECT payload_saneado->>'usuarioId' FROM outbox WHERE id=?",String.class,outboxId));
        // Do not wait for user locks while holding an outbox lock (request takes user first).
        var users=jdbc.queryForList("SELECT email_cifrado,estado FROM usuario WHERE id=? FOR UPDATE SKIP LOCKED",userId);
        if (users.isEmpty()) return;
        String type=(String)job.get("tipo");
        var user=users.getFirst();
        if (!(type.equals("EMAIL") ? "PENDENTE_EMAIL" : "ATIVO").equals(user.get("estado"))) {
            jdbc.update("UPDATE outbox SET estado='RECONCILIAR',updated_at=clock_timestamp() WHERE id=?",outboxId); return;
        }
        String token=protector.token(); UUID challenge=UUID.randomUUID();
        jdbc.update("UPDATE desafio_conta SET consumido_em=clock_timestamp() WHERE usuario_id=? AND tipo=? AND consumido_em IS NULL",userId,type);
        jdbc.update("INSERT INTO desafio_conta(id,usuario_id,tipo,token_hash,expira_em) VALUES (?,?,?,?,?)",challenge,userId,type,protector.tokenHash(token),java.sql.Timestamp.from(Instant.now().plus(type.equals("EMAIL")?emailTtl:recoveryTtl)));
        String state="ENVIADO";
        try {
            email.send(protector.decrypt((byte[])user.get("email_cifrado")),type.equals("EMAIL")?"Confirme seu e-mail":"Recuperação de acesso", "Use este código no aplicativo. Ele expira em "+(type.equals("EMAIL")?emailTtl:recoveryTtl).toMinutes()+" minutos: "+token);
        } catch (RuntimeException ex) {
            state="RECONCILIAR";
            jdbc.update("UPDATE desafio_conta SET consumido_em=clock_timestamp() WHERE id=?",challenge);
        }
        jdbc.update("UPDATE outbox SET estado=?,tentativas=tentativas+1,updated_at=clock_timestamp() WHERE id=?",state,outboxId);
    }
}
