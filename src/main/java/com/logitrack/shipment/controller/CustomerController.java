package com.logitrack.shipment.controller;

import com.logitrack.shipment.dto.CustomerRequest;
import com.logitrack.shipment.dto.CustomerResponse;
import com.logitrack.shipment.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@Tag(name = "Customers", description = "Registro y consulta de clientes")
@AllArgsConstructor
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerService customerService;

    @PostMapping
    @Operation(summary = "Registrar un cliente")
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CustomerRequest request) {
        log.info("POST /api/v1/customers");
        CustomerResponse response = customerService.create(request);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(response.id())
            .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener un cliente por id")
    public CustomerResponse findById(@PathVariable UUID id) {
        log.debug("GET /api/v1/customers/{}", id);
        return customerService.findById(id);
    }

    @GetMapping
    @Operation(summary = "Listar clientes paginados")
    public PagedModel<CustomerResponse> findAll(
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        log.debug("GET /api/v1/customers");
        Page<CustomerResponse> page = customerService.findAll(pageable);
        return new PagedModel<>(page);
    }
}
