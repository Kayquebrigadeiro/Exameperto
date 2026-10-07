package br.com.exameperto.identity;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final DataProtector protector;
    private final AccessTokens tokens;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final Duration sessionTtl, refreshTtl;
    private final com.github.benmanes.caffeine.cache.Cache<String,java.util.concurrent.atomic.AtomicInteger> loginAttempts = com.github.benmanes.caffeine.cache.Caffeine.newBuilder().maximumSize(10000).expireAfterWrite(Duration.ofMinutes(1)).build();
    private static final String DUMMY_HASH = Passwords.hash("synthetic-unused-timing-password");

    AuthService(JdbcTemplate jdbc, DataProtector protector, AccessTokens tokens, org.springframework.context.ApplicationEventPublisher events,
                @Value("${registration.session-ttl:PT15M}") Duration sessionTtl,
                @Value("${registration.refresh-ttl:P30D}") Duration refreshTtl) {
        this.jdbc = jdbc; this.protector = protector; this.tokens = tokens; this.events=events;
        this.sessionTtl = sessionTtl; this.refreshTtl = refreshTtl;
    }

    @Transactional
    public void verify(String token) {
        Map<String,Object> row = lockChallenge(token, "EMAIL");
        consume(row);
        jdbc.update("UPDATE usuario SET estado='ATIVO', email_verificado_em=clock_timestamp(), updated_at=clock_timestamp() WHERE id=? AND estado='PENDENTE_EMAIL'", row.get("usuario_id"));
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        if (!protector.configured() || !tokens.configured()) throw unavailable();
        String accountKey = java.util.HexFormat.of().formatHex(protector.lookup(normalize(request.email())));
        if (loginAttempts.get(accountKey,k->new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet()>5)
            throw new AuthException(HttpStatus.TOO_MANY_REQUESTS,"RATE_LIMITED","Tente novamente mais tarde.");
        Map<String,Object> user;
        try { user = jdbc.queryForMap("SELECT id,senha_hash,estado FROM usuario WHERE email_busca=? FOR UPDATE", protector.lookup(normalize(request.email()))); }
        catch (EmptyResultDataAccessException ex) { Passwords.matches(request.password(), DUMMY_HASH); throw unauthorized(); }
        boolean matches = Passwords.matches(request.password(), (String)user.get("senha_hash"));
        if (!matches || !"ATIVO".equals(user.get("estado"))) throw unauthorized();
        return createSession((UUID)user.get("id"), request.client().name(), UUID.randomUUID(), Instant.now().plus(refreshTtl));
    }

    // Replay revocation must commit even though the public response is 401.
    @Transactional(noRollbackFor = AuthException.class)
    public TokenResponse refresh(String raw, String client) {
        Map<String,Object> row = sessionByRefresh(raw);
        lockUser((UUID)row.get("usuario_id"));
        row = sessionByRefresh(raw);
        if (!client.equals(row.get("cliente"))) throw unauthorized();
        if (row.get("revogada_em") != null) {
            jdbc.update("UPDATE sessao SET revogada_em=coalesce(revogada_em,clock_timestamp()),updated_at=clock_timestamp() WHERE familia_id=?", row.get("familia_id"));
            throw unauthorized();
        }
        Instant expiry = ((java.sql.Timestamp)row.get("expira_em")).toInstant();
        if (!expiry.isAfter(Instant.now())) throw unauthorized();
        TokenResponse result = createSession((UUID)row.get("usuario_id"), client, (UUID)row.get("familia_id"), expiry);
        jdbc.update("UPDATE sessao SET revogada_em=clock_timestamp(),substituida_por=?,updated_at=clock_timestamp() WHERE id=?", tokens.session(result.accessToken()), row.get("id"));
        return result;
    }

    @Transactional
    public void logout(SessionPrincipal principal) {
        lockUser(principal.userId());
        // Also revokes a successor if logout races a rotation.
        jdbc.update("UPDATE sessao SET revogada_em=coalesce(revogada_em,clock_timestamp()),updated_at=clock_timestamp() WHERE familia_id=(SELECT familia_id FROM sessao WHERE id=?)", principal.sessionId());
        events.publishEvent(new SessionRevokedEvent(principal.sessionId()));
    }

    @Transactional
    public void completeRecovery(String token, String newPassword) {
        Map<String,Object> row = lockChallenge(token, "RECUPERACAO");
        consume(row);
        jdbc.update("UPDATE usuario SET senha_hash=?,updated_at=clock_timestamp() WHERE id=?", Passwords.hash(newPassword), row.get("usuario_id"));
        jdbc.update("UPDATE sessao SET revogada_em=coalesce(revogada_em,clock_timestamp()),updated_at=clock_timestamp() WHERE usuario_id=?", row.get("usuario_id"));
        jdbc.update("UPDATE desafio_conta SET consumido_em=coalesce(consumido_em,clock_timestamp()) WHERE usuario_id=? AND tipo='RECUPERACAO'", row.get("usuario_id"));
    }

    Optional<SessionPrincipal> authenticateAccess(String raw) {
        UUID id = tokens.session(raw);
        if (id == null) return Optional.empty();
        try {
            Map<String,Object> row = jdbc.queryForMap("SELECT s.usuario_id,s.cliente FROM sessao s JOIN usuario u ON u.id=s.usuario_id WHERE s.id=? AND s.access_hash=? AND s.revogada_em IS NULL AND s.access_expira_em>clock_timestamp() AND s.expira_em>clock_timestamp() AND u.estado='ATIVO'", id, protector.tokenHash(raw));
            return Optional.of(new SessionPrincipal((UUID)row.get("usuario_id"), id, (String)row.get("cliente")));
        } catch (EmptyResultDataAccessException ex) { return Optional.empty(); }
    }

    private Map<String,Object> sessionByRefresh(String raw) {
        if (raw == null || raw.length() != 43) throw unauthorized();
        try { return jdbc.queryForMap("SELECT * FROM sessao WHERE refresh_hash=?", protector.tokenHash(raw)); }
        catch (EmptyResultDataAccessException ex) { throw unauthorized(); }
    }
    private TokenResponse createSession(UUID user, String client, UUID family, Instant expiry) {
        UUID id = UUID.randomUUID(); Instant accessExpiry = Instant.now().plus(sessionTtl);
        String access = tokens.issue(user, id, accessExpiry), refresh = protector.token();
        jdbc.update("INSERT INTO sessao(id,usuario_id,familia_id,refresh_hash,expira_em,cliente,access_hash,access_expira_em) VALUES (?,?,?,?,?,?,?,?)", id,user,family,protector.tokenHash(refresh),java.sql.Timestamp.from(expiry),client,protector.tokenHash(access),java.sql.Timestamp.from(accessExpiry));
        return new TokenResponse(access,"Bearer",sessionTtl.toSeconds(),refresh);
    }
    private Map<String,Object> lockChallenge(String token, String type) {
        try {
            Map<String,Object> row = jdbc.queryForMap("SELECT id,usuario_id FROM desafio_conta WHERE token_hash=? AND tipo=?", protector.tokenHash(token), type);
            // Same lock order for login, reset, refresh and email issuance.
            String state=jdbc.queryForObject("SELECT estado FROM usuario WHERE id=? FOR UPDATE", String.class, row.get("usuario_id"));
            if (!(type.equals("EMAIL") ? "PENDENTE_EMAIL" : "ATIVO").equals(state)) throw unauthorized();
            return row;
        } catch (EmptyResultDataAccessException ex) { throw unauthorized(); }
    }
    private void consume(Map<String,Object> row) {
        if (jdbc.update("UPDATE desafio_conta SET consumido_em=clock_timestamp(),updated_at=clock_timestamp() WHERE id=? AND consumido_em IS NULL AND expira_em>clock_timestamp()", row.get("id")) != 1) throw unauthorized();
    }
    private void lockUser(UUID id) {
        String state = jdbc.queryForObject("SELECT estado FROM usuario WHERE id=? FOR UPDATE", String.class, id);
        if (!"ATIVO".equals(state)) throw unauthorized();
    }
    static String normalize(String email) { return email.trim().toLowerCase(java.util.Locale.ROOT); }
    static AuthException unauthorized() { return new AuthException(HttpStatus.UNAUTHORIZED,"UNAUTHENTICATED","Credenciais inválidas ou expiradas."); }
    static AuthException unavailable() { return new AuthException(HttpStatus.SERVICE_UNAVAILABLE,"INTEGRATION_UNAVAILABLE","O serviço de conta está indisponível."); }
    public record SessionPrincipal(UUID userId, UUID sessionId, String client) {}
    record SessionRevokedEvent(UUID sessionId) {}
    public static final class AuthException extends ResponseStatusException {
        private final ApiError body;
        AuthException(HttpStatus status, String code, String message) { super(status,message); body=new ApiError(code,message,UUID.randomUUID(),status.is5xxServerError() || status.value()==429); }
        ApiError body() { return body; }
    }
}
