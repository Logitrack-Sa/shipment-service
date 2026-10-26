package com.logitrack.shipment.repository;

import com.logitrack.shipment.entity.Address;
import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShipmentRepositoryTest extends AbstractMySqlRepositoryTest {
    @Autowired
    private ShipmentRepository repository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void storesBothAddressesAndLoadsRelationsWithoutNPlusOne() {
        for (int index = 0; index < 3; index++) {
            repository.save(shipment("LT-000000000" + index, "client" + index + "@test.com"));
        }
        entityManager.flush();
        entityManager.clear();
        var statistics = entityManager.getEntityManagerFactory()
                .unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        var page = repository.findByStatus(ShipmentStatus.PENDIENTE, PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).allSatisfy(shipment -> {
            assertThat(Hibernate.isInitialized(shipment.getCustomer())).isTrue();
            assertThat(shipment.getCustomer().getName()).isEqualTo("Customer");
            assertThat(shipment.getOrigin().getCity()).isEqualTo("Jujuy");
            assertThat(shipment.getDestination().getCity()).isEqualTo("Palpalá");
            assertThat(shipment.getCourierId()).isNull();
        });
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
    }

    @Test
    void findsShipmentByTrackingAndReportsDuplicates() {
        var saved = repository.saveAndFlush(shipment("LT-0123456789", "client@test.com"));

        assertThat(repository.findByTrackingCode(saved.getTrackingCode())).contains(saved);
        assertThat(repository.existsByTrackingCode(saved.getTrackingCode())).isTrue();
        assertThat(repository.findById(saved.getId())).contains(saved);
    }

    @Test
    void enforcesUniqueTrackingInTheDatabase() {
        repository.saveAndFlush(shipment("LT-0123456789", "first@test.com"));

        assertThatThrownBy(() -> repository.saveAndFlush(shipment("LT-0123456789", "second@test.com")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Shipment shipment(String trackingCode, String email) {
        var customer = new Customer("Customer", email, "3881234567");
        entityManager.persist(customer);
        return Shipment.builder()
                .trackingCode(trackingCode).customer(customer)
                .origin(new Address("Bolivia", "1239", "Jujuy", "4600"))
                .destination(new Address("Belgrano", "450", "Palpalá", "4612"))
                .weightKg(new BigDecimal("2.5")).widthCm(new BigDecimal("30"))
                .heightCm(new BigDecimal("20")).lengthCm(new BigDecimal("40"))
                .fee(new BigDecimal("2200.00")).build();
    }
}
