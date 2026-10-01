package br.com.exameperto.identity;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record LoginRequest(@NotBlank @Email @jakarta.validation.constraints.Size(max=254) String email, @NotBlank @jakarta.validation.constraints.Size(max=128) String password, @NotNull Client client) { enum Client { WEB, MOBILE } }
