package br.com.exameperto.identity;

import java.time.Instant;
import java.util.UUID;

record ProfileView(UUID id, String email, String name, String phone, String status,
                   Instant emailVerifiedAt, long version) {}
