package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record FundingReviewInput(@NotNull Decision decision, @NotNull UUID reconciliationEvidenceId,
                          @NotBlank @Size(max=200) String reasonCode) {
    enum Decision { CONFIRMADO, REJEITADO }
}
