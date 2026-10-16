package br.com.exameperto.identity;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Expurga GPS somente quando existe uma política versionada e validada. */
@Component
class TrackingRetentionJob {
    private static final Logger LOG = LoggerFactory.getLogger(TrackingRetentionJob.class);
    private static final String CATEGORY = "GPS";
    private static final String PURPOSE = "ENTREGA_RASTREAMENTO";
    private static final String TRIGGER = "POSICAO_EXPIRADA";
    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final int batchSize;
    private final int maxBatches;

    TrackingRetentionJob(JdbcTemplate jdbc,
                         @Value("${tracking.retention.enabled:false}") boolean enabled,
                         @Value("${tracking.retention.batch-size:100}") int batchSize,
                         @Value("${tracking.retention.max-batches-per-run:10}") int maxBatches) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.batchSize = Math.max(1, Math.min(batchSize, 1000));
        this.maxBatches = Math.max(1, Math.min(maxBatches, 100));
    }

    @Transactional
    @Scheduled(fixedDelayString = "${tracking.retention.queue-delay-ms:60000}",
               initialDelayString = "${tracking.retention.queue-delay-ms:60000}")
    void scheduledRun() {
        if (!enabled) return;
        try {
            runBatches();
        } catch (RuntimeException ex) {
            // Não expõe coordenadas, endereços ou conteúdo de uma posição nos logs.
            LOG.warn("expurgo GPS não executado; a próxima execução tentará novamente: {}", ex.getClass().getSimpleName());
        }
    }

    @Transactional
    int runBatches() {
        if (!enabled) return 0;
        Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtext('exame-perto:gps-retention'))", Boolean.class);
        if (!Boolean.TRUE.equals(locked)) return 0;
        Map<String, Object> policy = activePolicy();
        if (policy == null) return 0;
        int days = ((Number) policy.get("prazo_dias")).intValue();
        Instant cutoff = Instant.now().minusSeconds(days * 86_400L);
        int total = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            List<Position> candidates = jdbc.query(
                "SELECT p.id,p.designacao_id,p.sequencia,p.capturada_em " +
                "FROM posicao_tarefa p " +
                "WHERE p.capturada_em < ? AND NOT EXISTS (SELECT 1 FROM diario_expurgo_gps j WHERE j.designacao_id=p.designacao_id AND j.sequencia=p.sequencia) " +
                "ORDER BY p.capturada_em,p.id LIMIT ? FOR UPDATE SKIP LOCKED",
                (rs, row) -> new Position(rs.getObject("id", UUID.class), rs.getObject("designacao_id", UUID.class), rs.getLong("sequencia"), rs.getTimestamp("capturada_em").toInstant()),
                Timestamp.from(cutoff), batchSize);
            // A exceção é comparada por hash de referência, nunca por coordenadas.
            if (candidates.isEmpty()) break;
            List<Position> positions = candidates.stream().filter(position -> !hasException(position)).toList();
            if (positions.isEmpty()) continue;
            for (Position position : positions) {
                jdbc.update("INSERT INTO diario_expurgo_gps(id,politica_id,designacao_id,sequencia,referencia_hash,capturada_em) VALUES (?,?,?,?,?,?) ON CONFLICT (designacao_id,sequencia) DO NOTHING",
                    UUID.randomUUID(), policy.get("id"), position.assignmentId(), position.sequence(), hash(position.assignmentId(), position.sequence()), Timestamp.from(position.capturedAt()));
            }
            String placeholders = String.join(",", positions.stream().map(p -> "?").toList());
            Object[] ids = positions.stream().map(Position::id).toArray();
            total += jdbc.update("DELETE FROM posicao_tarefa WHERE id IN (" + placeholders + ")", ids);
        }
        if (total > 0) LOG.info("expurgo GPS concluído em lotes; posições removidas={}", total);
        return total;
    }

    private Map<String, Object> activePolicy() {
        List<Map<String, Object>> policies = jdbc.queryForList(
            "SELECT p.* FROM politica_retencao p JOIN usuario u ON u.id=p.responsavel_id AND u.estado='ATIVO' " +
            "WHERE p.categoria=? AND p.finalidade_codigo=? AND p.gatilho=? AND p.validada_em IS NOT NULL " +
            "AND p.validada_em<=clock_timestamp() AND p.desativada_em IS NULL AND p.prazo_dias IS NOT NULL " +
            "AND p.responsavel_id IS NOT NULL AND p.base_validada AND p.verificacao_descarte ORDER BY p.numero DESC LIMIT 1",
            CATEGORY, PURPOSE, TRIGGER);
        return policies.isEmpty() ? null : policies.get(0);
    }

    private boolean hasException(Position position) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM retencao_excecao WHERE categoria='GPS' AND referencia_hash=? AND ativa)", Boolean.class, hash(position.assignmentId(), position.sequence())));
    }

    static byte[] hash(UUID assignmentId, long sequence) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(ByteBuffer.allocate(24).putLong(assignmentId.getMostSignificantBits()).putLong(assignmentId.getLeastSignificantBits()).putLong(sequence).array());
        } catch (Exception ex) {
            throw new IllegalStateException("hash de referência indisponível", ex);
        }
    }

    private record Position(UUID id, UUID assignmentId, long sequence, Instant capturedAt) {}
}
