package com.logitrack.shipment.dto;

import com.logitrack.shipment.entity.ShipmentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeStatusRequest(

    @Schema(
        description = """
            Nuevo estado del envío. Transiciones permitidas:
            PENDIENTE -> EN_RUTA, PENDIENTE -> CANCELADO y EN_RUTA -> ENTREGADO.
            """,
        example = "EN_RUTA",
        allowableValues = {"PENDIENTE", "EN_RUTA", "ENTREGADO", "CANCELADO"},
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "El estado es obligatorio")
    ShipmentStatus status,

    @Schema(
        description = "Observación opcional que se guarda en el historial de estados",
        example = "Retirado en el depósito",
        maxLength = 255,
        nullable = true
    )
    @Size(max = 255, message = "La observación no puede superar los 255 caracteres")
    String observation
) {
}
