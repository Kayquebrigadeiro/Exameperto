package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record CancelOrderInput(@NotBlank @Size(max=80) String reasonCode) {}
