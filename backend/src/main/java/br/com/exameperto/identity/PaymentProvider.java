package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public interface PaymentProvider {
    boolean configured();
    String providerId();
    PaymentEvent authenticateAndParse(byte[] originalBody, Map<String,String> headers);
    record PaymentEvent(String externalEventId, UUID operationId, Result result, BigDecimal amount,
                        String currency, String beneficiaryReference, Instant occurredAt) {}
    enum Result { CONFIRMED, FAILED, UNCERTAIN }
}
