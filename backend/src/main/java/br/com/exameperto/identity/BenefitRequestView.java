package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

record BenefitRequestView(UUID id, UUID patientId, UUID institutionId, UUID policyId, int policyVersion,
    String status, Set<BenefitDimension> dimensions, String decision, BigDecimal percentage, String reason, long version,
    List<BenefitEvidenceView> evidence, List<BenefitReviewView> reviews) {}
