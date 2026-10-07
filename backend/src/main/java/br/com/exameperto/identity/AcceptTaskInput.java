package br.com.exameperto.identity;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

record AcceptTaskInput(@NotNull UUID vehicleLinkId, @NotNull UUID acceptedCancellationPolicyId) {}
