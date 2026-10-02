package br.com.exameperto.identity;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

record AcceptInvitationInput(@NotBlank @Size(min=43,max=43) String token) {}
