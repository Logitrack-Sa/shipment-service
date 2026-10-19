package com.logitrack.shipment.dto;

import com.logitrack.shipment.entity.ShipmentStatus;
import java.time.Instant;
import java.util.UUID;

public record StatusHistoryResponse(
        UUID id,
        ShipmentStatus previousStatus,
        ShipmentStatus newStatus,
        Instant changedAt,
        String observation) {
}
