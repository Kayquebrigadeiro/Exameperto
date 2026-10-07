package br.com.exameperto.identity;

import java.time.Instant;
import java.util.UUID;

record CustodyView(UUID id, UUID orderId, String status, String destinationType, Instant pickedUpAt,
                   Instant closedAt, long version) {}
