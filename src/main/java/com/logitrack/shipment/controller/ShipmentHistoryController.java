package com.logitrack.shipment.controller;

import com.logitrack.shipment.dto.ShipmentHistoryResponse;
import com.logitrack.shipment.service.ShipmentHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipments/{id}/history")
@RequiredArgsConstructor
@Tag(name = "Shipments")
public class ShipmentHistoryController {
    private final ShipmentHistoryService history;

    @GetMapping
    @Operation(summary = "Consultar historial de un envío autorizado")
    public List<ShipmentHistoryResponse> find(@PathVariable UUID id) { return history.find(id); }
}
