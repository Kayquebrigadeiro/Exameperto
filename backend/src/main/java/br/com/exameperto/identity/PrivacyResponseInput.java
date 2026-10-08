package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

record PrivacyResponseInput(@NotNull @Pattern(regexp="RESPONDIDA|NEGADA") String status,
                            @NotBlank @Size(max=4000) String response) {}
