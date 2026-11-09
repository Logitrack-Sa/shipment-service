package com.logitrack.shipmentservice.exception;

public class InvalidStateTransitionException extends BusinessRuleException {

    public static final String CODE = "INVALID_STATE_TRANSITION";

    public InvalidStateTransitionException(String resource, String from, String to) {
        super(CODE, "Transición de estado inválida para " + resource + ": " + from + " -> " + to);
    }

    public InvalidStateTransitionException(String message) {
        super(CODE, message);
    }
}
