package br.com.exameperto.identity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record ResetPasswordRequest(@NotBlank @Size(min=16, max=512) String token, @NotBlank @Size(min=12, max=128) String newPassword) {}
