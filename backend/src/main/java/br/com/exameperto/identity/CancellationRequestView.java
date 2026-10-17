package br.com.exameperto.identity;

import java.time.Instant;
import java.util.UUID;

record CancellationRequestView(UUID id, UUID orderId, String phase, String reasonCode,
                               String status, String financialTreatment, Instant createdAt) {}
