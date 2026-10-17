package com.logitrack.shipment.support;

import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.mapper.ShipmentMapper;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

public final class ShipmentEntities {
    private ShipmentEntities() {
    }

    public static Customer customer() {
        var customer = new Customer("Cliente", "cliente@test.com", "3881234567");
        ReflectionTestUtils.setField(customer, "id", ShipmentFixtures.CUSTOMER_ID);
        return customer;
    }

    public static Shipment shipment() {
        var shipment = new ShipmentMapper().toEntity(ShipmentFixtures.request(), customer(),
                ShipmentFixtures.TRACKING_CODE, new BigDecimal("2200.00"));
        ReflectionTestUtils.setField(shipment, "id", ShipmentFixtures.SHIPMENT_ID);
        return shipment;
    }
}
