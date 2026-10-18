package com.logitrack.shipment.dto;

import com.logitrack.shipment.entity.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ShipmentResponse(
        UUID id,
        @Schema(example = "LT-A1B2C3D4E5") String trackingCode,
        UUID customerId,
        AddressDto origin,
        AddressDto destination,
        BigDecimal weightKg,
        BigDecimal widthCm,
        BigDecimal heightCm,
        BigDecimal lengthCm,
        BigDecimal fee,
        ShipmentStatus status,
        @Schema(nullable = true) UUID courierId,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        String lastModifiedBy,
        Long version) {
}
