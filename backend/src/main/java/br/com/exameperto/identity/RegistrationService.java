package br.com.exameperto.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {
    private final EmailGateway emailGateway; private final JdbcTemplate jdbc; private final DataProtector protector;
    private final boolean privacyApproved; private final Duration emailTokenTtl; private final int attemptsPerMinute; private final int globalAttemptsPerMinute;
    private final com.github.benmanes.caffeine.cache.Cache<String, Window> attempts = com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(10000).expireAfterAccess(Duration.ofMinutes(2)).build(); private final Window global = new Window();
    @Autowired
    RegistrationService(EmailGateway emailGateway, JdbcTemplate jdbc, DataProtector protector,
        @Value("${registration.privacy-approved:false}") boolean privacyApproved,
        @Value("${registration.email-token-ttl:PT30M}") Duration emailTokenTtl,
        @Value("${registration.attempts-per-minute:5}") int attemptsPerMinute,
        @Value("${registration.global-attempts-per-minute:100}") int globalAttemptsPerMinute) {
        this.emailGateway = emailGateway; this.jdbc = jdbc; this.protector = protector; this.privacyApproved = privacyApproved; this.emailTokenTtl = emailTokenTtl; this.attemptsPerMinute = attemptsPerMinute; this.globalAttemptsPerMinute = globalAttemptsPerMinute;
    }
    RegistrationService(EmailGateway emailGateway, boolean privacyApproved, int attemptsPerMinute, int globalAttemptsPerMinute) {
        this.emailGateway = emailGateway; this.jdbc = null; this.protector = null; this.privacyApproved = privacyApproved; this.emailTokenTtl = Duration.ofMinutes(30); this.attemptsPerMinute = attemptsPerMinute; this.globalAttemptsPerMinute = globalAttemptsPerMinute;
    }
    @Transactional
    public void register(RegistrationRequest request, String clientKey) {
        if (!allow(clientKey)) throw error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "Tente novamente mais tarde.", true);
        if (!privacyApproved) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "POLICY_UNDEFINED", "O cadastro está temporariamente indisponível enquanto a política de privacidade não estiver validada.", false);
        if (!emailGateway.configured() || protector == null || jdbc == null || !protector.configured()) throw unavailable();
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT); byte[] lookup = protector.lookup(email);
        jdbc.execute((java.sql.Connection c) -> { var st=c.prepareStatement("SELECT pg_advisory_xact_lock(?)"); st.setLong(1, java.nio.ByteBuffer.wrap(lookup).getLong()); st.execute(); st.close(); return null; });
        if (jdbc.queryForObject("SELECT count(*) FROM usuario WHERE email_busca = ?", Long.class, lookup) > 0) return;
        UUID userId = UUID.randomUUID(); String token = protector.token();
        jdbc.update("INSERT INTO usuario(id,email_cifrado,email_busca,senha_hash,nome_cifrado,estado) VALUES (?,?,?,?,?,?)", userId, protector.encrypt(email), lookup, Passwords.hash(request.password()), protector.encrypt(request.name().trim()), "PENDENTE_EMAIL");
        jdbc.update("INSERT INTO desafio_conta(id,usuario_id,tipo,token_hash,expira_em) VALUES (?,?,?,?,?)", UUID.randomUUID(), userId, "EMAIL", protector.tokenHash(token), java.sql.Timestamp.from(Instant.now().plus(emailTokenTtl)));
        try { emailGateway.send(email, "Confirme seu e-mail", "Use este código de confirmação no aplicativo: " + token); } catch (RuntimeException ex) { throw unavailable(); }
    }
    private RegistrationException unavailable() { return error(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_UNAVAILABLE", "O serviço de e-mail não está disponível. Nenhuma conta foi criada.", true); }
    private boolean allow(String key) { return global.take(globalAttemptsPerMinute) && attempts.get(key, ignored -> new Window()).take(attemptsPerMinute); }
    private RegistrationException error(HttpStatus status, String code, String message, boolean retryable) { return new RegistrationException(status, new ApiError(code, message, UUID.randomUUID(), retryable)); }
    private static final class Window { private long started = System.currentTimeMillis(); private final AtomicInteger count = new AtomicInteger(); synchronized boolean take(int limit) { long now = System.currentTimeMillis(); if (now - started >= 60_000) { started = now; count.set(0); } return count.incrementAndGet() <= limit; } }
    static final class RegistrationException extends ResponseStatusException { private final ApiError body; RegistrationException(HttpStatus status, ApiError body) { super(status, body.message()); this.body = body; } ApiError body() { return body; } }
}
