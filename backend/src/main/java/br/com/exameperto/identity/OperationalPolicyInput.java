package br.com.exameperto.identity;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

record TariffPolicyInput(@NotBlank @Size(max=120) String scope,
                         @NotNull @DecimalMin("0.00") BigDecimal baseAmount,
                         @NotNull @DecimalMin("0.00") BigDecimal perKmAmount,
                         @NotNull @DecimalMin("0.00") BigDecimal perMinuteAmount,
                         @NotNull @DecimalMin("0.00") BigDecimal minimumAmount,
                         @Min(1) int roundingScale,
                         @Min(1) long quoteValiditySeconds,
                         @Min(1) long acceptanceDeadlineSeconds,
                         @Min(1) long executionDeadlineSeconds,
                         @NotNull @DecimalMin("0.00") BigDecimal returnAmount,
                         @NotBlank @Size(max=40) String returnCoverage,
                         @NotNull Instant startsAt, Instant endsAt) {
  boolean validWindow() { return endsAt == null || endsAt.isAfter(startsAt); }
}

record OperationalPolicyView(java.util.UUID id, String type, int number, int version,
                             String scope, String status, Instant startsAt, Instant endsAt,
                             Instant publishedAt, Instant deactivatedAt, boolean used) {}

record OperationalPolicyDetailView(OperationalPolicyView policy, java.util.Map<String,Object> values) {}

record OperationalPolicyHistoryView(String action, int version, String scope, Instant occurredAt) {}

record CancellationPolicyInput(@NotBlank @Size(max=120) String scope,
                               @NotBlank @Size(max=40) String beforeDesignation,
                               @NotBlank @Size(max=40) String afterDesignation,
                               @NotBlank @Size(max=40) String afterPickup,
                               @NotBlank @Size(max=40) String refundHandling,
                               @NotNull Instant startsAt, Instant endsAt) {
  boolean validWindow() { return endsAt == null || endsAt.isAfter(startsAt); }
}

record DesignationPolicyInput(@NotBlank @Size(max=120) String scope, @Min(1) int simultaneousTasks,
                              @NotNull Instant startsAt, Instant endsAt) {
  boolean validWindow() { return endsAt == null || endsAt.isAfter(startsAt); }
}
