package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;

record BenefitAppealInput(@NotBlank String reason, long requestVersion) {}
