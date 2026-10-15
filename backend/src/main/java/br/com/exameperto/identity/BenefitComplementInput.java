package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;

record BenefitComplementInput(@NotBlank String reason) {}
