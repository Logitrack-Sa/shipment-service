package com.logitrack.shipment.support;

import com.logitrack.shipment.dto.AddressDto;
import com.logitrack.shipment.dto.CreateShipmentRequest;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.entity.ShipmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ShipmentFixtures {
    public static final UUID CUSTOMER_ID = UUID.fromString("fba823ae-0e23-4e69-a16f-c71ae792e816");
    public static final UUID SHIPMENT_ID = UUID.fromString("33679937-aec6-4474-9a04-408ceab734c6");
    public static final String TRACKING_CODE = "LT-A1B2C3D4E5";

    private ShipmentFixtures() {
    }

    public static CreateShipmentRequest request() {
        return new CreateShipmentRequest(CUSTOMER_ID,
                new AddressDto("Av. Bolivia", "1239", "Jujuy", "4600"),
                new AddressDto("Belgrano", "450", "Palpalá", "4612"),
                new BigDecimal("2.5"), new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("40"));
    }

    public static ShipmentResponse response() {
        var request = request();
        var now = Instant.parse("2026-10-17T12:00:00Z");
        return new ShipmentResponse(SHIPMENT_ID, TRACKING_CODE, CUSTOMER_ID, request.origin(), request.destination(),
                request.weightKg(), request.widthCm(), request.heightCm(), request.lengthCm(),
                new BigDecimal("2200.00"), ShipmentStatus.PENDIENTE, null, now, now, "system", "system", 0L);
    }
}
