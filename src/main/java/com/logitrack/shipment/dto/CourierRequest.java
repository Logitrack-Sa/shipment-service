package com.logitrack.shipment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourierRequest(

    @NotBlank
    @Size(max = 120)
    String name,

    @NotBlank
    @Size(max = 50)
    String vehicleType
) {
}
