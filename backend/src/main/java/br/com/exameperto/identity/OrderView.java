package br.com.exameperto.identity;

import java.time.Instant;
import java.util.UUID;

record OrderView(UUID id, UUID patientId, String status, String coverageStatus, Instant createdAt, long version,
                 UUID pickupAuthorizationId, UUID pickupUnitId) {}
