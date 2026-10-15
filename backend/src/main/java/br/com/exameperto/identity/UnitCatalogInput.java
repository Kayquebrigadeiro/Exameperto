package br.com.exameperto.identity;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

record UnitCatalogInput(@NotBlank @Size(max=200) String name, @NotBlank @Size(max=120) String city,
                        @NotBlank @Size(min=2,max=2) String state) {}
record PartnershipInput(@NotBlank @Size(max=300) String evidenceReference,
                        @Size(max=1000) String note, @NotNull String status) {}
record ProtocolInput(int number, @NotBlank @Size(max=1000) String pickupInstructions,
                     @NotBlank @Size(max=200) String handoverEvidence,
                     @NotBlank @Size(max=1000) String returnInstructions,
                     boolean returnRequired, boolean returnCoverageConfirmed,
                     @NotNull Instant startsAt, Instant endsAt) {
    @AssertTrue boolean validWindow() { return endsAt == null || endsAt.isAfter(startsAt); }
}
