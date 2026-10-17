package com.logitrack.shipment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class FeeCalculatorTest {
    private final FeeCalculator calculator = new FeeCalculator(decimal("1000"), decimal("250"), decimal("5000"));

    @ParameterizedTest
    @CsvSource({
            "10, 10, 10, 10, 3500.00",
            "2.5, 30, 20, 40, 2200.00",
            "4.8, 30, 20, 40, 2200.00",
            "0.001, 0.01, 0.01, 0.01, 1000.25",
            "1.23458, 1, 1, 1, 1308.65",
            "1.23454, 1, 1, 1, 1308.64"
    })
    void calculatesExactFeeWithOneFinalRounding(String weight, String width, String height,
                                               String length, String expected) {
        BigDecimal fee = calculator.calculate(decimal(weight), decimal(width), decimal(height), decimal(length));

        assertThat(fee).isEqualTo(decimal(expected));
        assertThat(fee.scale()).isEqualTo(2);
    }

    @Test
    void doesNotRoundVolumetricWeightBeforeCalculatingThePrice() {
        var custom = new FeeCalculator(BigDecimal.ZERO, decimal("1"), decimal("3"));
        assertThat(custom.calculate(decimal("0.01"), decimal("1"), decimal("1"), decimal("1")))
                .isEqualTo(decimal("0.33"));
    }

    @ParameterizedTest
    @MethodSource("invalidValues")
    void rejectsInvalidWeightOrDimensions(BigDecimal invalid) {
        BigDecimal one = BigDecimal.ONE;
        assertThatIllegalArgumentException().isThrownBy(() -> calculator.calculate(invalid, one, one, one));
        assertThatIllegalArgumentException().isThrownBy(() -> calculator.calculate(one, invalid, one, one));
        assertThatIllegalArgumentException().isThrownBy(() -> calculator.calculate(one, one, invalid, one));
        assertThatIllegalArgumentException().isThrownBy(() -> calculator.calculate(one, one, one, invalid));
    }

    @Test
    void validatesConfigurationWithoutAllowingDivisionByZero() {
        assertThatIllegalArgumentException().isThrownBy(() -> new FeeCalculator(null, BigDecimal.ONE, BigDecimal.ONE));
        assertThatIllegalArgumentException().isThrownBy(() -> new FeeCalculator(decimal("-1"), BigDecimal.ONE, BigDecimal.ONE));
        assertThatIllegalArgumentException().isThrownBy(() -> new FeeCalculator(BigDecimal.ZERO, decimal("-1"), BigDecimal.ONE));
        assertThatIllegalArgumentException().isThrownBy(() -> new FeeCalculator(BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO));
    }

    static Stream<BigDecimal> invalidValues() {
        return Stream.of(null, BigDecimal.ZERO, decimal("-0.1"));
    }

    private static BigDecimal decimal(String value) {
        return new BigDecimal(value);
    }
}
