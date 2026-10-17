package com.logitrack.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateShipmentRequest(
        @NotNull UUID customerId,
        @NotNull @Valid AddressDto origin,
        @NotNull @Valid AddressDto destination,
        @NotNull @Positive @Digits(integer = 9, fraction = 3) @Schema(example = "2.5") BigDecimal weightKg,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) @Schema(example = "30") BigDecimal widthCm,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) @Schema(example = "20") BigDecimal heightCm,
        @NotNull @Positive @Digits(integer = 8, fraction = 2) @Schema(example = "40") BigDecimal lengthCm) {
}
