package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record DeliveryCode(@NotBlank @Size(min=20, max=512) String code) {}
