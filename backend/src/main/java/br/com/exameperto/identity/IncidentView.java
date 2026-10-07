package br.com.exameperto.identity;

import java.time.Instant;
import java.util.UUID;

record IncidentView(UUID id, UUID orderId, String type, String status, String description, Instant createdAt) {}
