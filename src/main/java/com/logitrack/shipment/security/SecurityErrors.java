package com.logitrack.shipment.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SecurityErrors {
    private final ObjectMapper mapper;

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        if (status == HttpStatus.UNAUTHORIZED) response.setHeader("WWW-Authenticate", "Bearer");
        mapper.writeValue(response.getOutputStream(), ErrorResponse.of(status.value(), status.getReasonPhrase(),
                status == HttpStatus.UNAUTHORIZED ? "UNAUTHORIZED" : "ACCESS_DENIED",
                status == HttpStatus.UNAUTHORIZED ? "Se requiere un JWT válido" : "No tenés permiso para este recurso",
                request.getRequestURI(), List.of()));
    }
}
