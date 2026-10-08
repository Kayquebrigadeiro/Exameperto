package br.com.exameperto.identity;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
record PrivacyRequestInput(@NotNull Type type,@NotBlank @Size(max=4000) String description){enum Type{ACESSO,CORRECAO,EXCLUSAO}}
