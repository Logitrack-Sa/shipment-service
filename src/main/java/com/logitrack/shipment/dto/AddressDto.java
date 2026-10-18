package com.logitrack.shipment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressDto(
        @NotBlank @Size(max = 150) @Schema(example = "Av. Bolivia") String street,
        @NotBlank @Size(max = 20) @Schema(example = "1239") String number,
        @NotBlank @Size(max = 100) @Schema(example = "San Salvador de Jujuy") String city,
        @NotBlank @Size(max = 20) @Schema(example = "4600") String postalCode) {
}
