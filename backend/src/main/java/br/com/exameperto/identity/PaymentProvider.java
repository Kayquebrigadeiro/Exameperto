package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface PaymentProvider {
    boolean configured();
    String providerId();
    Checkout createCheckout(CheckoutRequest request);
    PaymentEvent query(String providerOrderId, CheckoutRequest request);
    PaymentEvent authenticateAndParse(byte[] originalBody, Map<String,String> headers);
    record Checkout(String providerOrderId, String url, Instant expiresAt) {}
    record CheckoutRequest(UUID operationId, BigDecimal amount, String currency, String externalReference,
                           String beneficiaryReference, String payerEmail) {}
    record PaymentEvent(String externalEventId, UUID operationId, Result result, BigDecimal amount,
                        String currency, String beneficiaryReference, Instant occurredAt) {}
    enum Result { CONFIRMED, FAILED, UNCERTAIN }
}
