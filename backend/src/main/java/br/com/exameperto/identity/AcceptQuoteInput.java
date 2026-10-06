package br.com.exameperto.identity;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

record AcceptQuoteInput(
    @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal acceptedGrossAmount,
    @NotNull @DecimalMin("0.00") @Digits(integer=12,fraction=2) BigDecimal acceptedPatientAmount,
    @NotNull Currency acceptedCurrency,
    @NotNull UUID acceptedCancellationPolicyId,
    long acceptedOrderVersion
) { enum Currency { BRL } }
