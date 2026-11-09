package com.logitrack.shipment.service;

import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.mapper.CustomerMapper;
import com.logitrack.shipment.repository.CustomerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CustomerProvisioningTest {
    private final CustomerRepository customers = mock(CustomerRepository.class);
    private final CustomerProvisioningService service = new CustomerProvisioningService(customers, new CustomerMapper());
    private Jwt identity(UUID owner, boolean verified, String purpose) {
        return Jwt.withTokenValue("signed").header("alg", "RS256").subject(owner.toString())
                .claim("email_verified", verified).claim("purpose", purpose)
                .claim("name", "Ana").claim("email", "ANA@example.test").build();
    }
    @Test void createsOnlyTheSignedIdentityAndReusesItOnRetry() {
        var owner = UUID.randomUUID(); var stored = new Customer(owner, "Ana", "ana@example.test");
        when(customers.findByOwnerUserId(owner)).thenReturn(Optional.empty(), Optional.of(stored));
        when(customers.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        service.provision(identity(owner, true, "customer-provision"));
        service.provision(identity(owner, true, "customer-provision"));
        verify(customers, times(1)).saveAndFlush(argThat(c -> owner.equals(c.getOwnerUserId()) && "ana@example.test".equals(c.getEmail())));
    }
    @Test void normalSessionAndUnverifiedIdentityCannotProvision() {
        var owner = UUID.randomUUID();
        assertThatThrownBy(() -> service.provision(identity(owner, true, "session"))).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.provision(identity(owner, false, "customer-provision"))).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(customers);
    }
    @Test void matchingEmailDoesNotAuthorizeTakingOverExistingCustomer() {
        var owner = UUID.randomUUID();
        when(customers.findByOwnerUserId(owner)).thenReturn(Optional.empty());
        when(customers.existsByEmail("ana@example.test")).thenReturn(true);
        assertThatThrownBy(() -> service.provision(identity(owner, true, "customer-provision")))
                .isInstanceOf(com.logitrack.shipment.exception.DuplicateResourceException.class);
        verify(customers, never()).saveAndFlush(any());
    }
}
