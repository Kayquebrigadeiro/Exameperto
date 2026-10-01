package br.com.exameperto.identity;

import java.util.UUID;

public record ApiError(String code, String message, UUID correlationId, boolean retryable) {}
