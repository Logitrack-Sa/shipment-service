package com.logitrack.shipment.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Cálculo puro: no depende de Spring, persistencia ni reloj. */
public record FeeCalculator(BigDecimal base, BigDecimal pricePerKg, BigDecimal volumetricDivisor) {
    public FeeCalculator {
        requireNonNegative(base, "base");
        requireNonNegative(pricePerKg, "pricePerKg");
        requirePositive(volumetricDivisor, "volumetricDivisor");
    }

    public BigDecimal calculate(BigDecimal weightKg, BigDecimal widthCm,
                                BigDecimal heightCm, BigDecimal lengthCm) {
        requirePositive(weightKg, "weightKg");
        requirePositive(widthCm, "widthCm");
        requirePositive(heightCm, "heightCm");
        requirePositive(lengthCm, "lengthCm");
        BigDecimal volume = widthCm.multiply(heightCm).multiply(lengthCm);
        BigDecimal scaledWeight = weightKg.multiply(volumetricDivisor).max(volume);
        return base.multiply(volumetricDivisor).add(scaledWeight.multiply(pricePerKg))
                .divide(volumetricDivisor, 2, RoundingMode.HALF_UP);
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " debe ser mayor que cero");
        }
    }

    private static void requireNonNegative(BigDecimal value, String name) {
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException(name + " no puede ser negativo ni nulo");
        }
    }
}
