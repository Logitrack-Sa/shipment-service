
package com.logitrack.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Datos necesarios para registrar un cliente")
public record CustomerRequest(

    @Schema(
        description = "Nombre completo del cliente",
        example = "Juan Pérez",
        maxLength = 120,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank
    @Size(max = 120)
    String name,

    @Schema(
        description = "Correo electrónico del cliente",
        example = "juan.perez@example.com",
        maxLength = 180,
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank
    @Email(message = "Formato de correo invalido")
    @Size(max = 180)
    String email,

    @Schema(
        description = "Teléfono de contacto del cliente",
        example = "+5493815551234",
        requiredMode = Schema.RequiredMode.NOT_REQUIRED
    )
    @Pattern(
        regexp = "^\\+?[0-9\\s()-]{6,30}$",
        message = "formato de teléfono inválido"
    )
    String phone
) {
}
