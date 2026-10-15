package br.com.exameperto.identity;

import java.time.Instant;
import java.util.UUID;

record ReviewView(UUID id, UUID delivererId, UUID documentId, UUID analystId, String status,
    String decision, String reason, String category, String mime, long size,
    String documentStatus, Instant createdAt) {}
