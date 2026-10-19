package com.logitrack.shipment.exception;

public class CourierNotActiveException extends RuntimeException {

    public CourierNotActiveException(String message) {
        super(message);
    }
}
