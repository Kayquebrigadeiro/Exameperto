package br.com.exameperto.identity;

import java.time.Instant;
import java.util.UUID;

record UnitCatalogView(UUID id, String name, String city, String state, String registrationStatus,
                       PartnershipView partnership, java.util.List<ProtocolView> protocols, long version) {}
record PartnershipView(String status, String evidenceReference, String note, Instant acceptedAt) {}
record ProtocolView(UUID id, int number, String rules, boolean returnCoverageConfirmed,
                    Instant startsAt, Instant endsAt, String status) {}
record AvailableUnitView(UUID id, String label) {}
