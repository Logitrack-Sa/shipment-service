package com.logitrack.shipment.dto;

import com.logitrack.shipment.entity.ShipmentStatus;
import java.time.Instant;
import java.util.List;

/** Vista pública: no incluye identidades, direcciones ni observaciones internas. */
public record PublicTrackingResponse(String trackingCode, ShipmentStatus status,
                                     Instant createdAt, List<Movement> movements) {
    public record Movement(ShipmentStatus status, Instant changedAt) {}
}
