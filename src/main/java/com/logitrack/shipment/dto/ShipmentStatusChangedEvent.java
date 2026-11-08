package com.logitrack.shipment.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.logitrack.shipment.entity.ShipmentStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio publicado en RabbitMQ cuando un envío cambia de estado operativo
 * ({@code EN_RUTA}, {@code ENTREGADO} o {@code CANCELADO}).
 */
public record ShipmentStatusChangedEvent(
        UUID eventId,
        String shipmentId,
        String trackingCode,
        String courierId,
        ShipmentStatus previousStatus,
        ShipmentStatus currentStatus,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt
) {
}
