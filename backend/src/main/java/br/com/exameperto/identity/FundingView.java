package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record FundingView(UUID id, UUID programId, BigDecimal amount, String currency, String status,
                   UUID evidenceDocumentId, UUID reconciliationEvidenceId, Instant reviewedAt,
                   String reviewReasonCode, long version) {}
