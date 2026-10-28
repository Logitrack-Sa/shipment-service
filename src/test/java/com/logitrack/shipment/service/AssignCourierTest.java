package com.logitrack.shipment.service;

import com.logitrack.shipment.client.CourierLookup;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.support.ShipmentEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignCourierTest {
    @Mock com.logitrack.shipment.security.ShipmentAccess access;
    @Mock ShipmentRepository shipments;
    @Mock CourierLookup couriers;
    @Mock ShipmentCreator creator;
    @Mock TrackingCodeGenerator codes;
    @Mock ShipmentStatusRecorder history;
    ShipmentService service;
    Shipment shipment;
    UUID courierId = UUID.randomUUID();

    @BeforeEach void setup() {
        service = new ShipmentService(shipments, creator, codes, new ShipmentMapper(), couriers, history, access);
        shipment = ShipmentEntities.shipment();
        when(shipments.findById(shipment.getId())).thenReturn(Optional.of(shipment));
    }

    @Test void assignsAndReassignsByUuidWithoutLocalCourier() {
        assertThat(service.assignCourier(shipment.getId(), courierId).courierId()).isEqualTo(courierId);
        var other = UUID.randomUUID();
        assertThat(service.assignCourier(shipment.getId(), other).courierId()).isEqualTo(other);
        verify(couriers).requireAvailable(courierId);
        verifyNoInteractions(history);
    }

    @Test void lookupFailurePreservesPreviousAssignmentAndHistory() {
        var previous = UUID.randomUUID();
        shipment.assignCourier(previous);
        doThrow(new CourierNotActiveException(courierId)).when(couriers).requireAvailable(courierId);
        assertThatThrownBy(() -> service.assignCourier(shipment.getId(), courierId))
                .isInstanceOf(CourierNotActiveException.class);
        assertThat(shipment.getCourierId()).isEqualTo(previous);
        verifyNoInteractions(history);
    }

    @Test void unknownShipmentDoesNotCallRemoteService() {
        when(shipments.findById(shipment.getId())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignCourier(shipment.getId(), courierId))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(couriers, history);
    }
}
