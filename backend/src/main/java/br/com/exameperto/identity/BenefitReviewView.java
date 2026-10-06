package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.util.UUID;

record BenefitReviewView(UUID id, UUID requestId, String type, long requestVersion, UUID analystId,
    String status, String decision, BigDecimal percentage, String reason) {}
