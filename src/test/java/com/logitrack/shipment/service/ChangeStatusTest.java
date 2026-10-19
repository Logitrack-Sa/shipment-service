package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.entity.Courier;
import com.logitrack.shipment.entity.CourierStatus;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.CourierNotAssignedException;
import com.logitrack.shipment.exception.InvalidStateTransitionException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.CourierRepository;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.support.ShipmentEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static com.logitrack.shipment.support.ShipmentFixtures.SHIPMENT_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de {@link ShipmentService#changeStatus} para el cambio de estado de envíos:
 * transiciones válidas, registro en el historial, reglas de repartidor y rechazos sin efectos.
 */
@ExtendWith(MockitoExtension.class)
class ChangeStatusTest {

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
    void movesPendingToEnRouteWhenActiveCourierIsAssigned() {
        shipment.assignCourier(activeCourier());
        givenShipment();

        ShipmentResponse response = service.changeStatus(SHIPMENT_ID, ShipmentStatus.EN_RUTA, "Retirado");

        assertThat(response.status()).isEqualTo(ShipmentStatus.EN_RUTA);
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.EN_RUTA);
        verify(recorder).record(shipment, ShipmentStatus.PENDIENTE, ShipmentStatus.EN_RUTA, "Retirado");
    }

    @Test
    void allowsEnRouteToDelivered() {
        givenStatus(ShipmentStatus.EN_RUTA);

        ShipmentResponse response = service.changeStatus(SHIPMENT_ID, ShipmentStatus.ENTREGADO, null);

        assertThat(response.status()).isEqualTo(ShipmentStatus.ENTREGADO);
        verify(recorder).record(shipment, ShipmentStatus.EN_RUTA, ShipmentStatus.ENTREGADO, null);
    }

    @Test
    void allowsPendingToCancelledWithoutCourier() {
        givenShipment();

        ShipmentResponse response = service.changeStatus(SHIPMENT_ID, ShipmentStatus.CANCELADO, "Cancelado por el cliente");

        assertThat(response.status()).isEqualTo(ShipmentStatus.CANCELADO);
        verify(recorder).record(shipment, ShipmentStatus.PENDIENTE, ShipmentStatus.CANCELADO, "Cancelado por el cliente");
    }

    @Test
    void rejectsInvalidTransitionWithoutRecordingNorMutating() {
        givenStatus(ShipmentStatus.ENTREGADO);

        assertThatThrownBy(() -> service.changeStatus(SHIPMENT_ID, ShipmentStatus.CANCELADO, "intento"))
                .isInstanceOf(InvalidStateTransitionException.class);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.ENTREGADO);
        verifyNoInteractions(recorder);
    }

    @Test
    void invalidTransitionTakesPrecedenceOverCourierRules() {
        givenStatus(ShipmentStatus.ENTREGADO);

        assertThatThrownBy(() -> service.changeStatus(SHIPMENT_ID, ShipmentStatus.EN_RUTA, null))
                .isInstanceOf(InvalidStateTransitionException.class);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.ENTREGADO);
        verifyNoInteractions(recorder);
    }

    @Test
    void rejectsEnRouteWithoutAssignedCourier() {        givenShipment();

        assertThatThrownBy(() -> service.changeStatus(SHIPMENT_ID, ShipmentStatus.EN_RUTA, null))
                .isInstanceOf(CourierNotAssignedException.class);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PENDIENTE);
        verifyNoInteractions(recorder);
    }

    @Test
    void rejectsEnRouteWithInactiveCourier() {
        Courier courier = activeCourier();
        courier.changeStatus(CourierStatus.INACTIVO);
        shipment.assignCourier(courier);
        givenShipment();

        assertThatThrownBy(() -> service.changeStatus(SHIPMENT_ID, ShipmentStatus.EN_RUTA, null))
                .isInstanceOf(CourierNotActiveException.class);

        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.PENDIENTE);
        verifyNoInteractions(recorder);
    }

    @Test
    void rejectsUnknownShipmentWithoutRecording() {
        whenFindByIdEmpty();

        assertThatThrownBy(() -> service.changeStatus(UUID.randomUUID(), ShipmentStatus.CANCELADO, null))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(recorder, never()).record(any(), any(), any(), any());
    }

    private Courier activeCourier() {
        return new Courier("Juan", "Moto");
    }

    private void givenShipment() {
        when(shipments.findById(SHIPMENT_ID)).thenReturn(Optional.of(shipment));
    }

    private void givenStatus(ShipmentStatus status) {
        ReflectionTestUtils.setField(shipment, "status", status);
        givenShipment();
    }

    private void whenFindByIdEmpty() {
        when(shipments.findById(any())).thenReturn(Optional.empty());
    }
}
