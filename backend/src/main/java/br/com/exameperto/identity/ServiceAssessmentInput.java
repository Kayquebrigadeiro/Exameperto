package br.com.exameperto.identity;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

record ServiceAssessmentProposalInput(@NotNull @DecimalMin("0.00") BigDecimal driverAmount,
                                      @NotNull @DecimalMin("0.00") BigDecimal patientAmount,
                                      @NotNull @DecimalMin("0.00") BigDecimal institutionAmount,
                                      @NotBlank @Size(max=500) String reason,
                                      @NotEmpty List<@NotBlank @Size(max=300) String> evidenceReferences) {}

record ServiceAssessmentReviewInput(@NotBlank String decision,
                                    @NotBlank @Size(max=500) String reason) {}
