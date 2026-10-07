package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.time.Instant;

public interface PayoutProvider {
    boolean configured();
    String providerId();
    Outcome request(String reference, BigDecimal amount, String currency, String recipientReference);
    Outcome query(String reference);

    record Outcome(String eventId, String reference, Result result, BigDecimal amount,
                   String currency, String recipientReference, Instant occurredAt) {}
    enum Result { ACCEPTED, CONFIRMED, FAILED, UNCERTAIN }
}
