package com.logitrack.shipment.exception;

public class DuplicateResourceException extends DomainException {

    public static final String CODE = "DUPLICATE_RESOURCE";

    public DuplicateResourceException(String message) {
        super(CODE, message);
    }
}
