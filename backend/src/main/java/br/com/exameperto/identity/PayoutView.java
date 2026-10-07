package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record PayoutView(UUID id, UUID orderId, BigDecimal amount, String currency, String status,
                  String availability, String divergenceCode, Instant requestedAt,
                  Instant confirmedAt, long version) {}
