package com.logitrack.shipment.service;

import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.entity.ShipmentStatusHistory;
import com.logitrack.shipment.repository.ShipmentStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipmentStatusRecorderTest {
    private static final Instant FIXED_INSTANT = Instant.parse("2025-01-01T00:00:00Z");
    @Mock
    private ShipmentStatusHistoryRepository repository;
    private Clock clock = Clock.fixed(FIXED_INSTANT, ZoneId.of("UTC"));
    @InjectMocks
    private ShipmentStatusRecorder recorder;

    private Shipment shipment;

    @BeforeEach
    void setUp() {
        Customer customer = new Customer("Gonzalo Tester", "gonzalo@test.com", "555-0001");
        shipment = Shipment.builder()
                .customer(customer)
                .trackingCode("LT-ABC1234567")
                .fee(BigDecimal.valueOf(1000))
                .origin(new com.logitrack.shipment.entity.Address("Calle 1", "123", "Ciudad", "12345"))
                .destination(new com.logitrack.shipment.entity.Address("Calle 2", "456", "Ciudad2", "67890"))
                .weightKg(BigDecimal.ONE)
                .widthCm(BigDecimal.ONE)
                .heightCm(BigDecimal.ONE)
                .lengthCm(BigDecimal.ONE)
                .build();
        recorder = new ShipmentStatusRecorder(repository, clock);
    }

    @Test
    void recordsInitialTransitionWithNullPrevious() {
        recorder.record(shipment, null, ShipmentStatus.PENDIENTE, "Alta del envío");

        ArgumentCaptor<ShipmentStatusHistory> captor = ArgumentCaptor.forClass(ShipmentStatusHistory.class);
        verify(repository).save(captor.capture());
        ShipmentStatusHistory history = captor.getValue();

        assertThat(history.getShipment()).isEqualTo(shipment);
        assertThat(history.getPreviousStatus()).isNull();
        assertThat(history.getNewStatus()).isEqualTo(ShipmentStatus.PENDIENTE);
        assertThat(history.getChangedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(history.getObservation()).isEqualTo("Alta del envío");
    }

    @Test
    void recordsTransitionWithObservation() {
        recorder.record(shipment, ShipmentStatus.PENDIENTE, ShipmentStatus.EN_RUTA, "Asignado");

        ArgumentCaptor<ShipmentStatusHistory> captor = ArgumentCaptor.forClass(ShipmentStatusHistory.class);
        verify(repository).save(captor.capture());
        ShipmentStatusHistory history = captor.getValue();

        assertThat(history.getPreviousStatus()).isEqualTo(ShipmentStatus.PENDIENTE);
        assertThat(history.getNewStatus()).isEqualTo(ShipmentStatus.EN_RUTA);
        assertThat(history.getChangedAt()).isEqualTo(FIXED_INSTANT);
        assertThat(history.getObservation()).isEqualTo("Asignado");
    }
}
