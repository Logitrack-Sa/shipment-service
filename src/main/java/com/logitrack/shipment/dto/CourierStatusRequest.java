package com.logitrack.shipment.dto;

import com.logitrack.shipment.entity.CourierStatus;
import jakarta.validation.constraints.NotNull;

public record CourierStatusRequest(

    @NotNull
    CourierStatus status
) {
}
