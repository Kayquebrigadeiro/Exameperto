package br.com.exameperto.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record OrderInput(@NotNull UUID patientId, @NotNull @Valid Address origin, @NotNull @Valid Address destination,
                  @NotNull UUID recipientUserId, @NotNull UUID pickupAuthorizationDocumentId, @NotNull UUID pickupUnitId) {}
