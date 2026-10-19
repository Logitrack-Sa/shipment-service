package com.logitrack.shipment.exception;

public class CourierNotActiveException extends BusinessRuleException {

    public static final String CODE = "COURIER_NOT_ACTIVE";

    public CourierNotActiveException(Object courierId) {
        super(CODE, "El repartidor " + courierId + " no está activo");
    }

    public CourierNotActiveException(String message) {
        super(CODE, message);
    }
}
