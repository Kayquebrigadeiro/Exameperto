package br.com.exameperto.identity;

import java.time.Instant;
import java.util.UUID;

record OperationalIncidentView(UUID id, UUID orderId, String orderStatus, String type,
                               String status, String description, UUID custodyId,
                               String custodyStatus, String decision, String destinationType,
                               Instant createdAt) {}
