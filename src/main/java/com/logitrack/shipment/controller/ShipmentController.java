package com.logitrack.shipment.controller;

import com.logitrack.shipment.dto.CreateShipmentRequest;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.dto.PageResponse;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipments", description = "Registro y consulta de envíos")
public class ShipmentController {
    private final ShipmentService service;

    @GetMapping("/{id}")
    @Operation(summary = "Consultar un envío por su UUID")
    @ApiResponse(responseCode = "200", description = "Envío encontrado")
    @ApiResponse(responseCode = "400", description = "UUID inválido")
    @ApiResponse(responseCode = "404", description = "Envío inexistente")
    public ShipmentResponse findById(@PathVariable UUID id) {
        return service.findById(id);
    }

    @GetMapping("/tracking/{trackingCode}")
    @Operation(summary = "Consultar un envío por su código de seguimiento")
    @ApiResponse(responseCode = "200", description = "Envío encontrado")
    @ApiResponse(responseCode = "404", description = "Código de seguimiento inexistente")
    public ShipmentResponse findByTrackingCode(@PathVariable String trackingCode) {
        return service.findByTrackingCode(trackingCode);
    }

    @GetMapping
    @Operation(summary = "Listar envíos paginados, opcionalmente filtrados por estado")
    @ApiResponse(responseCode = "200", description = "Página de envíos")
    @ApiResponse(responseCode = "400", description = "Estado o paginación inválidos")
    public PageResponse<ShipmentResponse> findAll(
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var order = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
        return service.findAll(status, PageRequest.of(page, size, order));
    }

    @PostMapping
    @Operation(summary = "Registrar un envío")
    @ApiResponse(responseCode = "201", description = "Envío registrado")
    @ApiResponse(responseCode = "400", description = "Datos inválidos")
    @ApiResponse(responseCode = "404", description = "Cliente inexistente")
    @ApiResponse(responseCode = "409", description = "Conflicto al registrar el envío")
    public ResponseEntity<ShipmentResponse> create(@Valid @RequestBody CreateShipmentRequest request) {
        var shipment = service.createShipment(request);
        var location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(shipment.id()).toUri();
        return ResponseEntity.created(location).body(shipment);
    }
}
