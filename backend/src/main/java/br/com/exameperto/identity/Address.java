package br.com.exameperto.identity;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;

record Address(@NotBlank String street, @NotBlank String number, @NotBlank String district, @NotBlank String city,
               @NotBlank String state, @NotBlank String postalCode,
               @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
               @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude) {}
