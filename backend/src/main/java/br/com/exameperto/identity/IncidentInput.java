package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record IncidentInput(@NotBlank @Size(max=40) String type, @Size(max=500) String description) {}
