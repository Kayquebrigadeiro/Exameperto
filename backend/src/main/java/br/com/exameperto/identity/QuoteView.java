package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record QuoteView(UUID id, UUID orderId, String status, BigDecimal grossAmount, BigDecimal patientAmount,
                 BigDecimal subsidyAmount, int tariffVersion, String routeProvider, String routeReference,
                 int distanceMeters, int durationSeconds, boolean trafficIncluded, Instant calculatedAt,
                 Instant expiresAt, long version) {}
