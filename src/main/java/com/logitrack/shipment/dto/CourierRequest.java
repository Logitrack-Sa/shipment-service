package com.logitrack.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos necesarios para registrar un transportista")
public record CourierRequest(
    @Schema(
    description = "Nombre completo del transportista",
    example = "Juan Pérez",
    maxLength = 120,
    requiredMode = Schema.RequiredMode.REQUIRED
)
@NotBlank
@Size(max = 120)
    String name,

    @Schema(
        description = "Tipo de vehículo del transportista",
        example = "Camión",
        maxLength = 50,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank
    @Size(max = 50)
    String vehicleType
) {
}
