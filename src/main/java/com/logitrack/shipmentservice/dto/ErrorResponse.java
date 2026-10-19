package com.logitrack.shipmentservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        @JsonInclude(JsonInclude.Include.NON_EMPTY) List<FieldViolation> details) {

    public record FieldViolation(String field, String message) {
    }

    public static ErrorResponse of(int status, String error, String code, String message, String path,
            List<FieldViolation> details) {
        return new ErrorResponse(Instant.now(), status, error, code, message, path, details);
    }
}
