package br.com.exameperto.identity;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class AccountMailWorkerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final AccountMailService service = mock(AccountMailService.class);
    private final EmailGateway email = mock(EmailGateway.class);
    private final RecoveryGuard recovery = mock(RecoveryGuard.class);
    private final AccountMailWorker worker = new AccountMailWorker(jdbc, service, email, recovery);

    @Test void disabledProviderDoesNotPollTheDatabase() {
        when(email.configured()).thenReturn(false);
        worker.run();
        verifyNoInteractions(recovery, jdbc, service);
    }

    @Test void restorationGatePreventsOutboxMutation() {
        when(email.configured()).thenReturn(true);
        when(recovery.blocked()).thenReturn(true);
        worker.run();
        verifyNoInteractions(jdbc, service);
    }

    @Test void allowedWorkerDeliversPendingIdentifiers() {
        UUID id = UUID.randomUUID();
        when(email.configured()).thenReturn(true);
        when(recovery.blocked()).thenReturn(false);
        when(jdbc.queryForList(anyString(), eq(UUID.class))).thenReturn(List.of(id));
        worker.run();
        verify(service).deliver(id);
    }
}
