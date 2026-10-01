package br.com.exameperto.identity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
public record EmailInput(@NotBlank @Email @jakarta.validation.constraints.Size(max=254) String email) {}
