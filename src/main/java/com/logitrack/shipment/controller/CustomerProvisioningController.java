package com.logitrack.shipment.controller;

import com.logitrack.shipment.dto.CustomerResponse;
import com.logitrack.shipment.service.CustomerProvisioningService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CustomerProvisioningController {
    private final CustomerProvisioningService customers;

    @PostMapping("/api/v1/customers/provision")
    @Operation(summary = "Aprovisionar el perfil propio desde una identidad firmada por Security")
    public CustomerResponse provision(@AuthenticationPrincipal Jwt caller) {
        return customers.provision(caller);
    }
}
