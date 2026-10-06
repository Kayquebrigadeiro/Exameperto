package br.com.exameperto.identity;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

record FundingInput(@NotNull @DecimalMin("0.01") @Digits(integer=17,fraction=2) BigDecimal amount,
                    @NotNull Currency currency,
                    @NotNull java.util.UUID evidenceDocumentId,
                    @NotBlank @jakarta.validation.constraints.Size(max=160) String sourceReference) {
    enum Currency { BRL }
}
