package com.logitrack.shipmentservice.exception;

public class ResourceNotFoundException extends DomainException {

    public static final String CODE = "RESOURCE_NOT_FOUND";

    public ResourceNotFoundException(String resource, Object id) {
        super(CODE, resource + " no encontrado: " + id);
    }

    public ResourceNotFoundException(String message) {
        super(CODE, message);
    }
}
