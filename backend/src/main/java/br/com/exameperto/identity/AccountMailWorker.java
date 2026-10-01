package br.com.exameperto.identity;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
class AccountMailWorker {
    private final JdbcTemplate jdbc;
    private final AccountMailService service;
    AccountMailWorker(JdbcTemplate jdbc, AccountMailService service) { this.jdbc=jdbc; this.service=service; }
    @Scheduled(fixedDelayString="${email.queue-delay-ms:1000}", initialDelayString="${email.queue-delay-ms:1000}")
    void run() {
        try {
        for (UUID id:jdbc.queryForList("SELECT id FROM outbox WHERE tipo IN ('EMAIL','RECUPERACAO') AND estado='PENDENTE' ORDER BY created_at LIMIT 10",UUID.class)) {
            try { service.deliver(id); }
            catch (RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("ACCOUNT_MAIL_TRANSACTION_FAILED"); }
        }
        } catch (RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("ACCOUNT_MAIL_QUEUE_UNAVAILABLE"); }
    }
}
