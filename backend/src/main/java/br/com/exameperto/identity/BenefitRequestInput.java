package br.com.exameperto.identity;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import java.util.UUID;

record BenefitRequestInput(@NotNull UUID patientId, @NotNull UUID policyId, @NotEmpty Set<BenefitDimension> dimensions) {}
