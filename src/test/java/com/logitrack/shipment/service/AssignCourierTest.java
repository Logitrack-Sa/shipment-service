
package com.logitrack.shipment.service;

import com.logitrack.shipment.entity.Courier;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.exception.BusinessRuleException;
import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.CourierRepository;
import com.logitrack.shipment.repository.ShipmentRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AssignCourierTest {

    @Mock
    private ShipmentRepository shipments;

    @Mock
    private CourierRepository couriers;

    @Mock
    private ShipmentMapper mapper;

    @Mock
    private ShipmentCreator creator;

    @Mock
    private TrackingCodeGenerator trackingCodes;

    @InjectMocks
    private ShipmentService service;

    private UUID shipmentId;
    private UUID courierId;
    private Shipment shipment;
    private Courier courier;
    private ShipmentResponse response;

    @BeforeEach
    void setUp() {
        shipmentId = UUID.randomUUID();
        courierId = UUID.randomUUID();

        shipment = mock(Shipment.class);
        courier = mock(Courier.class);
        response = mock(ShipmentResponse.class);
    }

    @Test
    void shouldAssignActiveCourierToPendingShipment() {
        when(shipments.findById(shipmentId))
            .thenReturn(Optional.of(shipment));
        when(couriers.findById(courierId))
            .thenReturn(Optional.of(courier));
        when(shipment.getStatus())
            .thenReturn(ShipmentStatus.PENDIENTE);
        when(courier.isAvailable()).thenReturn(true);
        when(mapper.toResponse(shipment)).thenReturn(response);

        ShipmentResponse result =
            service.assignCourier(shipmentId, courierId);

        assertEquals(response, result);
        verify(shipment).assignCourier(courier);
        verify(mapper).toResponse(shipment);
    }

    @Test
    void shouldThrowWhenShipmentDoesNotExist() {
        when(shipments.findById(shipmentId))
            .thenReturn(Optional.empty());

        assertThrows(
            ResourceNotFoundException.class,
            () -> service.assignCourier(shipmentId, courierId)
        );

        verifyNoInteractions(couriers, mapper);
    }

    @Test
    void shouldThrowWhenCourierDoesNotExist() {
        when(shipments.findById(shipmentId))
            .thenReturn(Optional.of(shipment));
        when(couriers.findById(courierId))
            .thenReturn(Optional.empty());

        assertThrows(
            ResourceNotFoundException.class,
            () -> service.assignCourier(shipmentId, courierId)
        );

        verify(shipment, never()).assignCourier(any());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldThrowWhenCourierIsInactive() {
        when(shipments.findById(shipmentId))
            .thenReturn(Optional.of(shipment));
        when(couriers.findById(courierId))
            .thenReturn(Optional.of(courier));
        when(courier.isAvailable()).thenReturn(false);

        assertThrows(
            CourierNotActiveException.class,
            () -> service.assignCourier(shipmentId, courierId)
        );

        verify(shipment, never()).assignCourier(any());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldThrowWhenShipmentIsNotPending() {
        when(shipments.findById(shipmentId))
            .thenReturn(Optional.of(shipment));
        when(couriers.findById(courierId))
            .thenReturn(Optional.of(courier));
        when(courier.isAvailable()).thenReturn(true);
        when(shipment.getStatus())
            .thenReturn(ShipmentStatus.EN_RUTA);

        assertThrows(
            BusinessRuleException.class,
            () -> service.assignCourier(shipmentId, courierId)
        );

        verify(shipment, never()).assignCourier(any());
        verifyNoInteractions(mapper);
    }

    @Test
    void shouldAllowReassignmentWhileShipmentIsPending() {
        UUID anotherCourierId = UUID.randomUUID();
        Courier anotherCourier = mock(Courier.class);

        when(shipments.findById(shipmentId))
            .thenReturn(Optional.of(shipment));
        when(couriers.findById(anotherCourierId))
            .thenReturn(Optional.of(anotherCourier));
        when(anotherCourier.isAvailable()).thenReturn(true);
        when(shipment.getStatus())
            .thenReturn(ShipmentStatus.PENDIENTE);
        when(mapper.toResponse(shipment)).thenReturn(response);

        assertDoesNotThrow(
            () -> service.assignCourier(shipmentId, anotherCourierId)
        );

        verify(shipment).assignCourier(anotherCourier);
    }
}
