
package com.logitrack.shipment.repository;

import com.logitrack.shipment.entity.Customer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerRepositoryTest extends AbstractMySqlRepositoryTest {

    @Autowired
    private CustomerRepository repository;

    @Test
    void findsCustomerByEmail() {
        Customer customer = repository.saveAndFlush(
            new Customer(
                "Ana Pérez",
                "ana@example.com",
                "3881234567"
            )
        );

        assertThat(repository.findByEmail("ana@example.com"))
            .isPresent()
            .contains(customer);
    }

    @Test
    void enforcesUniqueEmailInTheDatabase() {
        repository.saveAndFlush(
            new Customer(
                "Ana Pérez",
                "ana@example.com",
                "3881234567"
            )
        );

        assertThatThrownBy(() ->
            repository.saveAndFlush(
                new Customer(
                    "Otra persona",
                    "ana@example.com",
                    "3887654321"
                )
            )
        ).isInstanceOf(DataIntegrityViolationException.class);
    }
}
