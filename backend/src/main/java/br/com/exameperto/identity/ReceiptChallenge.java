package br.com.exameperto.identity;

import java.time.Instant;

record ReceiptChallenge(String code, Instant expiresAt) {}
