package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

record VehicleInput(
    @NotBlank @Pattern(regexp="[A-Za-z0-9 -]{7,10}") String plate,
    @NotBlank @Size(max=80) String make,
    @NotBlank @Size(max=80) String model,
    @NotBlank @Size(max=40) String color,
    @NotNull Integer manufacturingYear,
    @NotNull Integer modelYear,
    @NotNull LinkType linkType,
    LocalDate validUntil
) {
    enum LinkType { PROPRIEDADE, LOCACAO, AUTORIZACAO }
}
