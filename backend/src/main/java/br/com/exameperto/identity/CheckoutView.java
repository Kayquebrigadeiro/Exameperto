package br.com.exameperto.identity;
import java.time.Instant;
import java.util.UUID;
record CheckoutView(String url,Instant expiresAt,UUID operationId) {}
