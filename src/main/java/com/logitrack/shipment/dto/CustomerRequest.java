package com.logitrack.shipment.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CustomerRequest(

    @NotBlank
    @Size(max = 120)
    String name,

    @NotBlank
    @Email (message = "Formato de correo invalido")
    @Size(max = 180)
    String email,

    @Pattern(regexp = "^\\+?[0-9\\s()-]{6,30}$", message = "formato de teléfono inválido")
    String phone
) {
}
