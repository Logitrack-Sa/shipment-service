package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.CustomerResponse;
import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.exception.DuplicateResourceException;
import com.logitrack.shipment.mapper.CustomerMapper;
import com.logitrack.shipment.repository.CustomerRepository;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerProvisioningService {
    private final CustomerRepository customers;
    private final CustomerMapper mapper;

    @Transactional
    public CustomerResponse provision(Jwt caller) {
        if (!"customer-provision".equals(caller.getClaimAsString("purpose"))
                || !Boolean.TRUE.equals(caller.getClaim("email_verified"))) {
            throw new AccessDeniedException("El alta requiere una identidad verificada de Security.");
        }
        UUID owner = UUID.fromString(caller.getSubject());
        var existing = customers.findByOwnerUserId(owner);
        if (existing.isPresent()) return mapper.toResponse(existing.get());
        String name = caller.getClaimAsString("name");
        String email = caller.getClaimAsString("email");
        if (name == null || name.isBlank() || name.length() > 200
                || email == null || email.isBlank() || email.length() > 254) {
            throw new AccessDeniedException("La identidad no contiene un perfil válido.");
        }
        email = email.strip().toLowerCase(Locale.ROOT);
        // Un email coincidente no autoriza apropiarse de un cliente preexistente.
        if (customers.existsByEmail(email)) {
            throw new DuplicateResourceException("El correo ya pertenece a otro perfil de cliente.");
        }
        return mapper.toResponse(customers.saveAndFlush(new Customer(owner, name.strip(), email)));
    }
}
