package br.com.exameperto.identity;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
final class RecoveryGuard {
    private final JdbcTemplate jdbc;
    RecoveryGuard(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    boolean blocked() {
        var accessAllowed = jdbc.queryForObject(
            "SELECT COALESCE((SELECT estado='NORMAL' FROM controle_restauracao WHERE singleton),false)",
            Boolean.class);
        return !Boolean.TRUE.equals(accessAllowed);
    }

    void requireExternalEffectsAllowed() {
        if (blocked()) throw new IllegalStateException("Efeitos externos bloqueados durante restauração.");
    }
}
