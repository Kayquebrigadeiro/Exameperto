package br.com.exameperto.identity;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

record DelivererInput(@NotNull LocalDate birthDate) {}
