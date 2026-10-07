package br.com.exameperto.identity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record LocationInput(@NotNull UUID assignmentId, @Positive long sequence, @NotNull Instant capturedAt,
                     @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
                     @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
                     @NotNull @DecimalMin("0") @DecimalMax("999999.99") BigDecimal accuracyMeters) {}
