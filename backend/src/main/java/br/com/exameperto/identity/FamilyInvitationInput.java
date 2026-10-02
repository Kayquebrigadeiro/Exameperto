package br.com.exameperto.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

record FamilyInvitationInput(@NotBlank @Email @Size(max=254) String recipientEmail,
                             @NotEmpty Set<FamilyScope> scopes) {}
