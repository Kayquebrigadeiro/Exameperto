package br.com.exameperto.identity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RegistrationService {
    private final EmailGateway emailGateway;
    private final boolean privacyApproved;
    private final int attemptsPerMinute;
    private final int globalAttemptsPerMinute;
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
    private final Window global = new Window();

    RegistrationService(EmailGateway emailGateway,
        @Value("${registration.privacy-approved:false}") boolean privacyApproved,
        @Value("${registration.attempts-per-minute:5}") int attemptsPerMinute,
        @Value("${registration.global-attempts-per-minute:100}") int globalAttemptsPerMinute) {
        this.emailGateway = emailGateway;
        this.privacyApproved = privacyApproved;
        this.attemptsPerMinute = attemptsPerMinute;
        this.globalAttemptsPerMinute = globalAttemptsPerMinute;
    }

    public void register(RegistrationRequest request, String clientKey) {
        if (!privacyApproved) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "POLICY_UNDEFINED",
            "O cadastro está temporariamente indisponível enquanto a política de privacidade não estiver validada.", false);
        if (!allow(clientKey)) throw error(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
            "Tente novamente mais tarde.", true);
        if (!emailGateway.configured()) throw error(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_UNAVAILABLE",
            "O serviço de e-mail não está disponível. Nenhuma conta foi criada.", true);
        // 03A intentionally has no success path: a real provider and the full account flow belong to ticket 03.
        throw error(HttpStatus.SERVICE_UNAVAILABLE, "INTEGRATION_UNAVAILABLE",
            "O serviço de e-mail não está disponível. Nenhuma conta foi criada.", true);
    }

    private boolean allow(String key) {
        return global.take(globalAttemptsPerMinute) && attempts.computeIfAbsent(key, ignored -> new Window()).take(attemptsPerMinute);
    }

    private ResponseStatusException error(HttpStatus status, String code, String message, boolean retryable) {
        return new RegistrationException(status, new ApiError(code, message, UUID.randomUUID(), retryable));
    }

    private static final class Window {
        private long started = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger();
        synchronized boolean take(int limit) {
            long now = System.currentTimeMillis();
            if (now - started >= 60_000) { started = now; count.set(0); }
            return count.incrementAndGet() <= limit;
        }
    }

    static final class RegistrationException extends ResponseStatusException {
        private final ApiError body;
        RegistrationException(HttpStatus status, ApiError body) { super(status, body.message()); this.body = body; }
        ApiError body() { return body; }
    }
}
