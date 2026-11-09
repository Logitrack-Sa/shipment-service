package com.logitrack.shipment.controller;

import com.logitrack.shipment.dto.PublicTrackingResponse;
import com.logitrack.shipment.service.PublicTrackingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PublicTrackingController {
    private final PublicTrackingService service;

    @GetMapping("/api/v1/shipments/public-tracking/{code}")
    @Operation(summary = "Consultar el estado público de un envío sin iniciar sesión")
    @SecurityRequirements
    public ResponseEntity<PublicTrackingResponse> find(
            @PathVariable @Pattern(regexp = "LT-[A-Z0-9]{10}",
                    message = "Ingresá un código de seguimiento válido") String code) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.find(code));
    }
}
