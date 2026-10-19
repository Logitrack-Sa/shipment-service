package com.logitrack.shipment.exception;

public class ShipmentCannotBeCancelledException extends BusinessRuleException {

    public static final String CODE = "SHIPMENT_CANNOT_BE_CANCELLED";

    public ShipmentCannotBeCancelledException(Object shipmentId, String status) {
        super(CODE, "El envío " + shipmentId + " no puede cancelarse porque está en estado " + status);
    }

    public ShipmentCannotBeCancelledException(String message) {
        super(CODE, message);
    }
}
