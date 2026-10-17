package com.logitrack.shipment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.logitrack.shipment.dto.CustomerRequest;
import com.logitrack.shipment.dto.CustomerResponse;
import com.logitrack.shipment.entity.Customer;

import com.logitrack.shipment.exception.DuplicateResourceException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.mapper.CustomerMapper;
import com.logitrack.shipment.repository.CustomerRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    @Mock
    private CustomerRepository customerRepository;

    @Spy
    private CustomerMapper customerMapper = new CustomerMapper();

    @InjectMocks
    private CustomerService customerService;

    @Test
    void createNormalizesEmailAndPersistsCustomer() {
        when(customerRepository.existsByEmail("ana@mail.com")).thenReturn(false);
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> inv.getArgument(0));

        CustomerResponse response = customerService.create(
            new CustomerRequest("  Ana  ", "  Ana@Mail.com ", "  "));

        assertThat(response.name()).isEqualTo("Ana");
        assertThat(response.email()).isEqualTo("ana@mail.com");
        assertThat(response.phone()).isNull();
    }

    @Test
    void createThrowsWhenEmailAlreadyExists() {
        when(customerRepository.existsByEmail("ana@mail.com")).thenReturn(true);

        assertThatThrownBy(() -> customerService.create(
            new CustomerRequest("Ana", "ana@mail.com", null)))
            .isInstanceOf(DuplicateResourceException.class);    }

    @Test
    void findByIdThrowsWhenCustomerDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(customerRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customerService.findById(id))
            .isInstanceOf(ResourceNotFoundException.class);    }
}
