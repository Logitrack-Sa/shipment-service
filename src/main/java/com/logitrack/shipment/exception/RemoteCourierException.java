package com.logitrack.shipment.exception;

import org.springframework.http.HttpStatus;

public class RemoteCourierException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public RemoteCourierException(HttpStatus status, String code) {
        super("No se pudo validar el repartidor en el servicio remoto");
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() { return status; }
    public String code() { return code; }
}
