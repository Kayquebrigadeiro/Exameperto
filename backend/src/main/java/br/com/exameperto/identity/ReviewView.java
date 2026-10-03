package br.com.exameperto.identity;

import java.util.UUID;

record ReviewView(UUID id, UUID delivererId, UUID documentId, UUID analystId, String status, String decision, String reason) {}
