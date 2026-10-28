
package com.logitrack.shipment.controller;

import com.logitrack.shipment.dto.CustomerRequest;
import com.logitrack.shipment.dto.CustomerResponse;
import com.logitrack.shipment.dto.ErrorResponse;
import com.logitrack.shipment.service.CustomerService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/customers")
@Tag(
    name = "Customers",
    description = "Registro, consulta y listado paginado de clientes"
)
@AllArgsConstructor
public class CustomerController {

    private static final Logger log =
        LoggerFactory.getLogger(CustomerController.class);

    private final CustomerService customerService;

    @PostMapping
    @Operation(
        summary = "Registrar un cliente",
        description = """
            Registra un nuevo cliente y devuelve sus datos.
            La respuesta incluye la ubicación del recurso creado
            mediante el encabezado Location.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "201",
            description = "Cliente registrado correctamente",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CustomerResponse.class),
                examples = @ExampleObject(
                    name = "Cliente creado",
                    value = """
                        {
                          "id": "550e8400-e29b-41d4-a716-446655440000"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Solicitud inválida o errores de validación",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "status": 400,
                          "code": "VALIDATION_ERROR",
                          "message": "La solicitud contiene datos inválidos"
                        }
                        """
                )
            )
        ),
        @ApiResponse(
            responseCode = "422",
            description = "La solicitud no cumple una regla de negocio",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    public ResponseEntity<CustomerResponse> create(
        @Valid
        @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Datos del cliente que se desea registrar",
            required = true,
            content = @Content(
                schema = @Schema(implementation = CustomerRequest.class)
            )
        )
        CustomerRequest request) {

        log.info("POST /api/v1/customers");

        CustomerResponse response = customerService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.id())
            .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Obtener un cliente por ID",
        description = "Busca un cliente mediante su identificador UUID."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Cliente encontrado",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = CustomerResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "400",
            description = "El identificador tiene un formato inválido",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        ),
        @ApiResponse(
            responseCode = "404",
            description = "No existe un cliente con el ID indicado",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class),
                examples = @ExampleObject(
                    value = """
                        {
                          "status": 404,
                          "code": "CUSTOMER_NOT_FOUND",
                          "message": "No se encontró el cliente solicitado"
                        }
                        """
                )
            )
        )
    })
    public CustomerResponse findById(
        @Parameter(
            description = "Identificador UUID del cliente",
            required = true,
            example = "550e8400-e29b-41d4-a716-446655440000"
        )
        @PathVariable UUID id) {

        log.debug("GET /api/v1/customers/{}", id);

        return customerService.findById(id);
    }

    @GetMapping
    @Operation(
        summary = "Listar clientes paginados",
        description = """
            Devuelve los clientes en páginas.
            Permite controlar el número de página, el tamaño
            y el criterio de ordenamiento.
            """
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Listado de clientes obtenido correctamente",
            useReturnTypeSchema = true
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Los parámetros de paginación u ordenamiento son inválidos",
            content = @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)
            )
        )
    })
    public PagedModel<CustomerResponse> findAll(
        @ParameterObject
        @PageableDefault(size = 20, sort = "createdAt")
        Pageable pageable) {

        log.debug("GET /api/v1/customers");

        Page<CustomerResponse> page = customerService.findAll(pageable);

        return new PagedModel<>(page);
    }
}
