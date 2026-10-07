package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record LocationView(UUID assignmentId, long sequence, Instant capturedAt, Instant receivedAt,
                    BigDecimal latitude, BigDecimal longitude, BigDecimal accuracyMeters,
                    boolean stale, boolean lowAccuracy) {}
