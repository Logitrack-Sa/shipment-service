package com.logitrack.shipment.exception;

public class BusinessRuleException extends DomainException {

    public static final String CODE = "BUSINESS_RULE_VIOLATION";

    public BusinessRuleException(String code, String message) {
        super(code, message);
    }

    public BusinessRuleException(String message) {
        this(CODE, message);
    }
}
