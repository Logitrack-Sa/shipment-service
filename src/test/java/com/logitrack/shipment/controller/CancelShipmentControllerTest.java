package com.logitrack.shipment.controller;

import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.exception.ShipmentCannotBeCancelledException;
import com.logitrack.shipment.service.ShipmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShipmentController.class)
class CancelShipmentControllerTest {

    private static final String ENDPOINT = "/api/v1/shipments/{id}/cancellation";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShipmentService service;

    @Test
    void returns200WithCancelledShipment() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        when(service.cancel(shipmentId)).thenReturn(response(shipmentId, ShipmentStatus.CANCELADO));

        mockMvc.perform(post(ENDPOINT, shipmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shipmentId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        verify(service).cancel(shipmentId);
    }

    @Test
    void returns404WhenShipmentDoesNotExist() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        when(service.cancel(shipmentId)).thenThrow(new ResourceNotFoundException("Envío", shipmentId));

        mockMvc.perform(post(ENDPOINT, shipmentId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        verify(service).cancel(shipmentId);
    }

    @Test
    void returns422WhenShipmentIsNotPending() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        when(service.cancel(shipmentId))
                .thenThrow(new ShipmentCannotBeCancelledException(shipmentId, "EN_RUTA"));

        mockMvc.perform(post(ENDPOINT, shipmentId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SHIPMENT_CANNOT_BE_CANCELLED"));

        verify(service).cancel(shipmentId);
    }

    @Test
    void returns400WhenShipmentIdIsNotAValidUuid() throws Exception {
        mockMvc.perform(post("/api/v1/shipments/no-es-uuid/cancellation"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    private ShipmentResponse response(UUID shipmentId, ShipmentStatus status) {
        Instant now = Instant.now();
        return new ShipmentResponse(shipmentId, "LT-A1B2C3D4E5", UUID.randomUUID(), null, null,
                new BigDecimal("2.000"), new BigDecimal("10.00"), new BigDecimal("15.00"),
                new BigDecimal("20.00"), new BigDecimal("25.00"), status, null, now, now, "system", "system", 0L);
    }
}
