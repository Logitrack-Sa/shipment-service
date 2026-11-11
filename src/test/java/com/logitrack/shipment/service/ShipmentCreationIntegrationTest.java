package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.CreateShipmentRequest;
import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.repository.CustomerRepository;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.repository.ShipmentStatusHistoryRepository;
import com.logitrack.shipment.support.ShipmentFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@ActiveProfiles("test")
class ShipmentCreationIntegrationTest {
    @Autowired
    private ShipmentService service;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private ShipmentRepository shipments;
    @Autowired
    private ShipmentStatusHistoryRepository history;
    @MockitoSpyBean
    private ShipmentStatusRecorder recorder;

    @BeforeEach
    void clearDatabase() {
        history.deleteAll();
        shipments.deleteAll();
        customers.deleteAll();
    }

    @Test
    void commitsShipmentAndExactlyOneInitialHistoryEntry() {
        var created = service.createShipment(requestForExistingCustomer());
        var entries = history.findByShipmentIdOrderByChangedAtAsc(created.id());

        assertThat(created.trackingCode()).matches("LT-[A-Z0-9]{10}");
        assertThat(created.status()).isEqualTo(ShipmentStatus.PENDIENTE);
        assertThat(created.fee()).isEqualByComparingTo(new BigDecimal("2200.00"));
        assertThat(created.createdAt()).isNotNull();
        assertThat(created.courierId()).isNull();
        assertThat(shipments.count()).isEqualTo(1);
        assertThat(entries).singleElement().satisfies(entry -> {
            assertThat(entry.getPreviousStatus()).isNull();
            assertThat(entry.getNewStatus()).isEqualTo(ShipmentStatus.PENDIENTE);
            assertThat(entry.getChangedAt()).isNotNull();
        });
    }

    @Test
    void rollsBackShipmentWhenHistoryRecordingFails() {
        var request = requestForExistingCustomer();
        ShipmentStatusRecorder target = AopTestUtils.getUltimateTargetObject(recorder);
        doThrow(new IllegalStateException("Fallo de historial")).when(target)
                .record(any(), isNull(), eq(ShipmentStatus.PENDIENTE), anyString());

        assertThatThrownBy(() -> service.createShipment(request)).isInstanceOf(IllegalStateException.class);
        assertThat(shipments.count()).isZero();
        assertThat(history.count()).isZero();
    }

    @Test
    void missingCustomerLeavesNoShipmentOrHistory() {
        var fixture = ShipmentFixtures.request();
        var request = new CreateShipmentRequest(UUID.randomUUID(), fixture.origin(), fixture.destination(),
                fixture.weightKg(), fixture.widthCm(), fixture.heightCm(), fixture.lengthCm());

        assertThatThrownBy(() -> service.createShipment(request)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(shipments.count()).isZero();
        assertThat(history.count()).isZero();
    }

    private CreateShipmentRequest requestForExistingCustomer() {
        var customer = customers.saveAndFlush(new Customer("Cliente", "cliente@test.com", "3881234567"));
        var fixture = ShipmentFixtures.request();
        return new CreateShipmentRequest(customer.getId(), fixture.origin(), fixture.destination(),
                fixture.weightKg(), fixture.widthCm(), fixture.heightCm(), fixture.lengthCm());
    }
}
