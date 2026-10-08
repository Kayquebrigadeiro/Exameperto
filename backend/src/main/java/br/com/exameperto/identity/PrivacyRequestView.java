package br.com.exameperto.identity;

import java.time.Instant;
import java.util.UUID;

record PrivacyRequestView(UUID id, String protocol, String type, String status, Instant createdAt,
                          String response, String purgeStatus, Instant executedAt, Instant verifiedAt,
                          int procedurePending, int preserved, long version) {}
