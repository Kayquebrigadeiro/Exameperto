package br.com.exameperto.identity;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ProfileService {
    private final JdbcTemplate jdbc;
    private final DataProtector protector;

    ProfileService(JdbcTemplate jdbc, DataProtector protector) {
        this.jdbc = jdbc;
        this.protector = protector;
    }

    ProfileView own(UUID userId) {
        return view(row(userId, false));
    }

    @Transactional
    ProfileView update(UUID userId, ProfileInput input, long expectedVersion) {
        Map<String, Object> current = row(userId, true);
        long version = ((Number) current.get("version")).longValue();
        if (version != expectedVersion) throw error(HttpStatus.PRECONDITION_FAILED, "VERSION_MISMATCH", "O perfil mudou; recarregue antes de atualizar.");
        String name = input.name().trim();
        String phone = input.phone() == null || input.phone().isBlank() ? null : input.phone().trim();
        int changed = jdbc.update("UPDATE usuario SET nome_cifrado=?,telefone_cifrado=?,version=version+1,updated_at=clock_timestamp() WHERE id=? AND version=? AND estado='ATIVO'",
            protector.encrypt(name), phone == null ? null : protector.encrypt(phone), userId, expectedVersion);
        if (changed != 1) throw error(HttpStatus.PRECONDITION_FAILED, "VERSION_MISMATCH", "O perfil mudou; recarregue antes de atualizar.");
        return own(userId);
    }

    private Map<String, Object> row(UUID userId, boolean lock) {
        try {
            return jdbc.queryForMap("SELECT id,email_cifrado,nome_cifrado,telefone_cifrado,estado,email_verificado_em,version FROM usuario WHERE id=? AND estado='ATIVO'" + (lock ? " FOR UPDATE" : ""), userId);
        } catch (EmptyResultDataAccessException ex) {
            throw error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Perfil não encontrado.");
        }
    }

    private ProfileView view(Map<String, Object> row) {
        Timestamp verified = (Timestamp) row.get("email_verificado_em");
        byte[] phone = (byte[]) row.get("telefone_cifrado");
        return new ProfileView((UUID) row.get("id"), protector.decrypt((byte[]) row.get("email_cifrado")),
            protector.decrypt((byte[]) row.get("nome_cifrado")), phone == null ? null : protector.decrypt(phone),
            (String) row.get("estado"), verified == null ? null : verified.toInstant(), ((Number) row.get("version")).longValue());
    }

    private ProfileException error(HttpStatus status, String code, String message) { return new ProfileException(status, code, message); }

    static final class ProfileException extends org.springframework.web.server.ResponseStatusException {
        private final ApiError body;
        ProfileException(HttpStatus status, String code, String message) { super(status, message); body = new ApiError(code, message, UUID.randomUUID(), status.is5xxServerError()); }
        ApiError body() { return body; }
    }
}
