package com.logitrack.shipment.dto;

import com.logitrack.shipment.entity.CourierStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CourierStatusRequest(

    @Schema(
        description = "Nuevo estado del transportista",
        example = "ACTIVO",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull

    CourierStatus status
) {
}
