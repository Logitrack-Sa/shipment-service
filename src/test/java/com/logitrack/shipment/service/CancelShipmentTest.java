package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.entity.Courier;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.exception.ShipmentCannotBeCancelledException;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.CourierRepository;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.support.ShipmentEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static com.logitrack.shipment.support.ShipmentFixtures.SHIPMENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelShipmentTest {

    @Mock
    private ShipmentRepository shipments;
    @Mock
    private CourierRepository couriers;
    @Mock
    private ShipmentStatusRecorder recorder;
    @Mock
    private ShipmentCreator creator;
    @Mock
    private TrackingCodeGenerator trackingCodes;

    private ShipmentService service;
    private Shipment shipment;

    @BeforeEach
    void setUp() {
        var mapper = new ShipmentMapper();
        service = new ShipmentService(shipments, creator, trackingCodes, mapper, couriers, recorder);
        shipment = ShipmentEntities.shipment();
    }

    @Test
    void cancelsPendingShipmentAndRecordsHistory() {
        givenShipment();

        ShipmentResponse response = service.cancel(SHIPMENT_ID);

        assertThat(response.status()).isEqualTo(ShipmentStatus.CANCELADO);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCELADO);
        verify(recorder).record(shipment, ShipmentStatus.PENDIENTE, ShipmentStatus.CANCELADO,
                "Cancelación del envío");
    }

    @Test
    void keepsAssignedCourierAfterCancellation() {
        Courier courier = new Courier("Juan", "Moto");
        shipment.assignCourier(courier);
        ReflectionTestUtils.setField(courier, "id", UUID.randomUUID());
        givenShipment();

        ShipmentResponse response = service.cancel(SHIPMENT_ID);

        assertThat(shipment.getCourier()).isSameAs(courier);
        assertThat(response.courierId()).isEqualTo(courier.getId());
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"EN_RUTA", "ENTREGADO", "CANCELADO"})
    void rejectsCancellationWhenShipmentIsNotPending(ShipmentStatus status) {
        ReflectionTestUtils.setField(shipment, "status", status);
        givenShipment();

        assertThatThrownBy(() -> service.cancel(SHIPMENT_ID))
                .isInstanceOf(ShipmentCannotBeCancelledException.class)
                .hasMessageContaining(status.name());

        assertThat(shipment.getStatus()).isEqualTo(status);
        verifyNoInteractions(recorder);
    }

    @Test
    void rejectsUnknownShipmentWithoutRecording() {
        when(shipments.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(recorder, never()).record(any(), any(), any(), anyString());
    }

    private void givenShipment() {
        when(shipments.findById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
    }
}
