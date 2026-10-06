package br.com.exameperto.identity;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BenefitCalculatorTest {
    @Test void roundsAndAcceptsPolicyBoundaries() {
        assertThat(BenefitCalculator.percentage(new BigDecimal("33.335"), new BigDecimal("100")))
            .isEqualByComparingTo("33.34");
        assertThat(BenefitCalculator.percentage(new BigDecimal("100"), new BigDecimal("100")))
            .isEqualByComparingTo("100.00");
    }

    @Test void rejectsOutsideSyntheticPolicy() {
        assertThatThrownBy(() -> BenefitCalculator.percentage(new BigDecimal("100.01"), new BigDecimal("100")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BenefitCalculator.percentage(new BigDecimal("20"), new BigDecimal("19")))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
