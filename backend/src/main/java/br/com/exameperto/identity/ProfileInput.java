package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record ProfileInput(@NotBlank @Size(max=160) String name, @Size(max=40) String phone) {}
