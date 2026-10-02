package br.com.exameperto.identity;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

record GrantView(UUID id, UUID patientId, UUID familyUserId, Set<FamilyScope> scopes,
                 Instant expiresAt, Instant revokedAt, long version, UUID invitationId) {}
