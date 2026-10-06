package br.com.exameperto.identity;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class BenefitCalculator {
    private BenefitCalculator() {}
    static BigDecimal percentage(BigDecimal proposed, BigDecimal policyMaximum) {
        if (proposed == null || policyMaximum == null || proposed.signum() < 0 || policyMaximum.signum() < 0 || policyMaximum.compareTo(BigDecimal.valueOf(100)) > 0 || proposed.compareTo(policyMaximum) > 0)
            throw new IllegalArgumentException("percentual fora da política");
        return proposed.setScale(2, RoundingMode.HALF_UP);
    }
}
