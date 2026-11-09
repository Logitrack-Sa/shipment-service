package com.logitrack.shipment.exception;

/**
 * Excepción lanzada cuando un usuario intenta registrar un envío sin haber verificado su
 * dirección de correo electrónico ({@code email_verified == false} en el token JWT).
 */
public class AccountNotVerifiedException extends DomainException {

    private static final String DEFAULT_MESSAGE =
            "No puedes solicitar envíos hasta que confirmes tu dirección de correo electrónico.";

    public AccountNotVerifiedException() {
        super("ACCOUNT_NOT_VERIFIED", DEFAULT_MESSAGE);
    }

    public AccountNotVerifiedException(String message) {
        super("ACCOUNT_NOT_VERIFIED", message);
    }
}
