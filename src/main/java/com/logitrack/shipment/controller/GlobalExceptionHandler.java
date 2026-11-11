package com.logitrack.shipment.controller;

import com.logitrack.shipment.dto.ErrorResponse;
import com.logitrack.shipment.exception.DuplicateResourceException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorResponse> notFound(ResourceNotFoundException exception, HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler({DuplicateResourceException.class, DataIntegrityViolationException.class})
    ResponseEntity<ErrorResponse> conflict(HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "DATA_CONFLICT", "La solicitud entra en conflicto con los datos existentes",
                request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var details = exception.getBindingResult().getFieldErrors().stream()
                .map(field -> new ErrorResponse.FieldViolation(field.getField(), field.getDefaultMessage())).toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Revisá los campos de la solicitud", request, details);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            ConstraintViolationException.class, HandlerMethodValidationException.class})
    ResponseEntity<ErrorResponse> invalidRequest(HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "El formato de la solicitud es inválido", request, List.of());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message,
                                               HttpServletRequest request, List<ErrorResponse.FieldViolation> details) {
        return ResponseEntity.status(status).body(new ErrorResponse(Instant.now(), status.value(),
                status.getReasonPhrase(), code, message, request.getRequestURI(), details));
    }
}
