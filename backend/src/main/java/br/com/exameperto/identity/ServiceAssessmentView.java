package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

record ServiceAssessmentView(UUID id, UUID orderId, UUID incidentId, UUID cancellationId,
                             String modality, String status, UUID responsibleId, UUID reviewerId,
                             BigDecimal driverAmount, BigDecimal patientAmount, BigDecimal institutionAmount,
                             BigDecimal balanceToRelease, BigDecimal balanceToReturn,
                             String reason, List<String> evidenceReferences, long version,
                             Instant updatedAt, boolean hasProposal) {}
