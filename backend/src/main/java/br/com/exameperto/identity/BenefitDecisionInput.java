package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

record BenefitDecisionInput(@NotNull Decision decision, @NotNull BigDecimal percentage, @NotBlank String reason) {
    enum Decision { APROVADA, REJEITADA }
}
