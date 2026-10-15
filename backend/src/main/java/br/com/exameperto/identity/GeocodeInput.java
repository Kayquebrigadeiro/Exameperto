package br.com.exameperto.identity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
record GeocodeInput(@NotNull @Valid Address address) {}
