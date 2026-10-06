package br.com.exameperto.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

record OrderAddressUpdateInput(@NotNull @Valid Address origin, @NotNull @Valid Address destination, long version) {}
