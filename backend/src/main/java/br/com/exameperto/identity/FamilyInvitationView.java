package br.com.exameperto.identity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

record FamilyInvitationView(UUID id, UUID patientId, String status, Set<FamilyScope> scopes,
                            Instant expiresAt, long version) {}
