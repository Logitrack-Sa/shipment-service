package com.logitrack.shipment.controller;

import com.logitrack.shipment.dto.CourierRequest;
import com.logitrack.shipment.dto.CourierResponse;
import com.logitrack.shipment.dto.CourierStatusRequest;
import com.logitrack.shipment.entity.CourierStatus;
import com.logitrack.shipment.service.CourierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/couriers")
@Tag(name = "Couriers", description = "Registro, consulta y activación de repartidores")
public class CourierController {

    private static final Logger log = LoggerFactory.getLogger(CourierController.class);

    private final CourierService courierService;

    @PostMapping
    @Operation(summary = "Registrar un repartidor")
    public ResponseEntity<CourierResponse> create(@Valid @RequestBody CourierRequest request) {
        log.info("POST /api/v1/couriers");
        CourierResponse response = courierService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.id())
            .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    @Operation(summary = "Listar repartidores, con filtro opcional por estado")
    public PagedModel<CourierResponse> findAll(
        @RequestParam(required = false) CourierStatus status,
        @ParameterObject @PageableDefault(size = 20, sort = "name") Pageable pageable) { // <-- Agregado @ParameterObject
        log.debug("GET /api/v1/couriers status={}", status);
        Page<CourierResponse> page = courierService.findAll(status, pageable);
        return new PagedModel<>(page);
    }


    @GetMapping("/{id}")
    @Operation(summary = "Obtener un repartidor por id")
    public CourierResponse findById(@PathVariable UUID id) {
        log.debug("GET /api/v1/couriers/{}", id);
        return courierService.findById(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Activar o desactivar un repartidor")
    public CourierResponse changeStatus(
        @PathVariable UUID id,
        @Valid @RequestBody CourierStatusRequest request) {
        log.info("PATCH /api/v1/couriers/{}/status -> {}", id, request.status());
        return courierService.changeStatus(id, request.status());
    }
}
