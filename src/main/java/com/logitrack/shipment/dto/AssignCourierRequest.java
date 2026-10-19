package com.logitrack.shipment.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignCourierRequest(
    @NotNull(message = "El ID del repartidor es obligatorio")
    UUID courierId
) {
}
