package com.logitrack.shipmentservice.controller;

import com.logitrack.shipmentservice.dto.ErrorResponse;
import com.logitrack.shipmentservice.dto.ErrorResponse.FieldViolation;
import com.logitrack.shipmentservice.exception.BusinessRuleException;
import com.logitrack.shipmentservice.exception.DuplicateResourceException;
import com.logitrack.shipmentservice.exception.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;


@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException exception,
            HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResource(DuplicateResourceException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorResponse> handleBusinessRule(BusinessRuleException exception,
            HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldViolation> details = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldViolation(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Revisá los campos de la solicitud", request,
                details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldViolation> details = exception.getConstraintViolations().stream()
                .map(violation -> new FieldViolation(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return error(HttpStatus.BAD_REQUEST, "CONSTRAINT_VIOLATION", "Revisá los parámetros de la solicitud", request,
                details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "El cuerpo de la solicitud no es un JSON válido",
                request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        String detail = "Valor inválido: " + exception.getValue();
        return error(HttpStatus.BAD_REQUEST, "TYPE_MISMATCH", "El parámetro '" + exception.getName()
                + "' tiene un tipo inválido", request, List.of(new FieldViolation(exception.getName(), detail)));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                "La entidad fue modificada por otra operación, reintentá", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Error no controlado en {} {}", request.getMethod(), request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "Ocurrió un error inesperado, intentá nuevamente", request, List.of());
    }

    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code, String message,
            HttpServletRequest request, List<FieldViolation> details) {
        ErrorResponse body = ErrorResponse.of(status.value(), status.getReasonPhrase(), code, message,
                request.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }
}
