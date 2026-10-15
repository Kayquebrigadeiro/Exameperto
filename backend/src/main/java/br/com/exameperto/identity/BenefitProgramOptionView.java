package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.util.UUID;

record BenefitProgramOptionView(UUID id, UUID institutionId, String institutionName,
                                String code, String name, UUID benefitRequestId,
                                BigDecimal percentage) {}
