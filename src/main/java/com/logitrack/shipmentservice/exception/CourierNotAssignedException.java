package com.logitrack.shipmentservice.exception;

public class CourierNotAssignedException extends BusinessRuleException {

    public static final String CODE = "COURIER_NOT_ASSIGNED";

    public CourierNotAssignedException(Object shipmentId) {
        super(CODE, "El envío " + shipmentId + " no tiene repartidor asignado");
    }

    public CourierNotAssignedException(String message) {
        super(CODE, message);
    }
}
