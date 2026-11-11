package com.logitrack.shipment.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Validated
@ConfigurationProperties("logitrack.fee")
public record FeeProperties(
        @NotNull @DecimalMin("0") BigDecimal base,
        @NotNull @DecimalMin("0") BigDecimal pricePerKg,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal volumetricDivisor) {
}
