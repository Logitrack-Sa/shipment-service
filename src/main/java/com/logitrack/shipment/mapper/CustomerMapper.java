package com.logitrack.shipment.mapper;

import com.logitrack.shipment.dto.CustomerRequest;
import com.logitrack.shipment.dto.CustomerResponse;
import com.logitrack.shipment.entity.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public Customer toEntity(CustomerRequest request) {
        return new Customer(request.name(), request.email(), request.phone());
    }

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
            customer.getId(),
            customer.getName(),
            customer.getEmail(),
            customer.getPhone(),
            customer.getCreatedAt()
        );
    }
}
