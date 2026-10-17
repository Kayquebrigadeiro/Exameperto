package br.com.exameperto.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

record IncidentResolutionInput(@NotBlank @Size(max=32) String decision,
                               @NotBlank @Size(max=20) String destinationType,
                               UUID destinationUnitId,
                               @NotBlank @Size(max=1000) String instructions,
                               @Size(max=300) String evidenceReference) {}
