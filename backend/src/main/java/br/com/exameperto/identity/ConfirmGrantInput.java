package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

record ConfirmGrantInput(@NotNull UUID invitationId, @NotEmpty Set<FamilyScope> scopes,
                         @NotNull Instant expiresAt, @NotBlank @Size(max=128) String password) {}
