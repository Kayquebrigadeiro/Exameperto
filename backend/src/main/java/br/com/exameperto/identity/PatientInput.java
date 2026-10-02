package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;

record PatientInput(@NotBlank @Pattern(regexp="\\d{11}") String cpf, @NotNull LocalDate birthDate) {}
