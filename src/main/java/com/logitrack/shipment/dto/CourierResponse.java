package com.logitrack.shipment.dto;

import com.logitrack.shipment.entity.CourierStatus;
import java.time.Instant;
import java.util.UUID;

public record CourierResponse(
    UUID id,
    String name,
    String vehicleType,
    CourierStatus status,
    Instant createdAt
) {
}
