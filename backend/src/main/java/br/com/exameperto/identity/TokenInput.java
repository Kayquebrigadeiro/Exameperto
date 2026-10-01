package br.com.exameperto.identity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record TokenInput(@NotBlank @Size(min=16, max=512) String token) {}
