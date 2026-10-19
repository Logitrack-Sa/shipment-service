package com.logitrack.shipment.controller;

import com.logitrack.shipment.dto.ChangeStatusRequest;
import com.logitrack.shipment.dto.AssignCourierRequest;
import com.logitrack.shipment.dto.CreateShipmentRequest;
import com.logitrack.shipment.dto.ErrorResponse;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.dto.PageResponse;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shipments")
@RequiredArgsConstructor
@Tag(name = "Shipments", description = "Registro, consulta y cambio de estado de envíos")
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

    @PutMapping("/{id}/courier")
    @Operation(summary = "Asignar o reasignar un repartidor a un envío pendiente")
    @ApiResponse(responseCode = "200", description = "Repartidor asignado correctamente")
    @ApiResponse(responseCode = "400", description = "Datos de entrada inválidos")
    @ApiResponse(responseCode = "404", description = "Envío o repartidor inexistente")
    @ApiResponse(responseCode = "422", description = "No se puede realizar la asignación")
    public ShipmentResponse assignCourier(
        @PathVariable UUID id,
        @Valid @RequestBody AssignCourierRequest request) {
        return service.assignCourier(id, request.courierId());
    }

    @PatchMapping("/{id}/status")
    @Operation(
        summary = "Cambiar el estado de un envío",
        description = """
            Aplica la máquina de estados del dominio y registra el cambio en el historial.
            Transiciones permitidas:
            - PENDIENTE -> EN_RUTA (requiere un repartidor ACTIVO asignado)
            - PENDIENTE -> CANCELADO
            - EN_RUTA -> ENTREGADO
            ENTREGADO y CANCELADO son estados terminales.
            Cualquier otra transición es rechazada y no modifica el envío ni genera historial.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Estado actualizado correctamente",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ShipmentResponse.class),
                examples = @ExampleObject(
                    name = "Envío en ruta",
                    value = """
                        {
                          "id": "550e8400-e29b-41d4-a716-446655440000",
                          "status": "EN_RUTA",
                          "courierId": "7f9a1c2e-3b4d-4e5f-8a9b-0c1d2e3f4a5b"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "JSON inválido, campo status ausente o estado desconocido",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "status": 400,
                          "code": "MALFORMED_REQUEST",
                          "message": "El cuerpo de la solicitud no es un JSON válido"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Envío inexistente",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "Transición inválida, repartidor no asignado o repartidor inactivo",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = {
                    @ExampleObject(
                        name = "Transición inválida",
                        value = """
                            {
                              "status": 422,
                              "code": "INVALID_STATE_TRANSITION",
                              "message": "Transición de estado inválida para Envío: ENTREGADO -> CANCELADO"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Sin repartidor",
                        value = """
                            {
                              "status": 422,
                              "code": "COURIER_NOT_ASSIGNED",
                              "message": "El envío 550e8400-e29b-41d4-a716-446655440000 no tiene repartidor asignado"
                            }
                            """
                    ),
                    @ExampleObject(
                        name = "Repartidor inactivo",
                        value = """
                            {
                              "status": 422,
                              "code": "COURIER_NOT_ACTIVE",
                              "message": "El repartidor 7f9a1c2e-3b4d-4e5f-8a9b-0c1d2e3f4a5b no está activo"
                            }
                            """
                    )
                }
            )
        )
    })
    public ShipmentResponse changeStatus(
            @Parameter(description = "UUID del envío", required = true) @PathVariable UUID id,
            @Valid
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                description = "Nuevo estado y observación opcional del cambio",
                required = true,
                content = @Content(schema = @Schema(implementation = ChangeStatusRequest.class))
            )
            @RequestBody ChangeStatusRequest request) {
        return service.changeStatus(id, request.status(), request.observation());
    }
}
