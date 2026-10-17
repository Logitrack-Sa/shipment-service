package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.CustomerRequest;
import com.logitrack.shipment.dto.CustomerResponse;
import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.exception.DuplicateResourceException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.mapper.CustomerMapper;
import com.logitrack.shipment.repository.CustomerRepository;
import java.util.Locale;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    private final CustomerRepository customerRepository;
    private final CustomerMapper customerMapper;


    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        CustomerRequest normalized = normalize(request);

        if (customerRepository.existsByEmail(normalized.email())) {
            log.warn("Registro de cliente rechazado: email ya existente");
            //throw new DuplicateCustomerEmailException();
            throw new DuplicateResourceException("Ya existe un cliente registrado con el email: " + normalized.email());

        }

        Customer saved = customerRepository.save(customerMapper.toEntity(normalized));
        log.info("Cliente creado id={}", saved.getId());
        return customerMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(UUID id) {
        log.debug("Buscando cliente id={}", id);
        return customerRepository.findById(id)
            .map(customerMapper::toResponse)
            .orElseThrow(() -> {
                log.warn("Cliente no encontrado id={}", id);
               // return new CustomerNotFoundException(id);
                return new ResourceNotFoundException("Cliente", id);
            });
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> findAll(Pageable pageable) {
        log.debug("Listando clientes page={} size={}", pageable.getPageNumber(), pageable.getPageSize());
        return customerRepository.findAll(pageable).map(customerMapper::toResponse);
    }

    /** Normaliza los datos de entrada: el email se guarda en minúsculas y sin espacios. */
    private CustomerRequest normalize(CustomerRequest request) {
        return new CustomerRequest(
            request.name().trim(),
            request.email().trim().toLowerCase(Locale.ROOT),
            blankToNull(request.phone())
        );
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
