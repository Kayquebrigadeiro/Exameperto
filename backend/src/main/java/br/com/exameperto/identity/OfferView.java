package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.util.UUID;

record OfferView(UUID orderId, String originCity, String destinationCity, int distanceMeters,
                 BigDecimal grossAmount, String currency, long orderVersion, UUID cancellationPolicyId) {}
