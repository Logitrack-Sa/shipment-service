
package com.logitrack.shipment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.exception.BusinessRuleException;
import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.service.ShipmentService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.ActiveProfiles("test")
@WebMvcTest(ShipmentController.class)
class AssignCourierControllerTest {

    private static final String ENDPOINT =
        "/api/v1/shipments/{id}/courier";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ShipmentService service;

    @Test
    void shouldReturn200WhenCourierIsAssigned() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();

        ShipmentResponse response = new ShipmentResponse(
            shipmentId,
            "TRK123456789",
            UUID.randomUUID(),
            null,
            null,
            new BigDecimal("2.000"),
            new BigDecimal("10.00"),
            new BigDecimal("15.00"),
            new BigDecimal("20.00"),
            new BigDecimal("25.00"),
            ShipmentStatus.PENDIENTE,
            courierId,
            Instant.now(),
            Instant.now(),
            "test-user",
            "test-user",
            0L
        );

        when(service.assignCourier(shipmentId, courierId))
            .thenReturn(response);

        mockMvc.perform(put(ENDPOINT, shipmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {
                                  "courierId": "%s"
                                }
                                """.formatted(courierId)))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(
                MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(shipmentId.toString()))
            .andExpect(jsonPath("$.courierId").value(courierId.toString()))
            .andExpect(jsonPath("$.status").value("PENDIENTE"));

        verify(service).assignCourier(shipmentId, courierId);
    }

    @Test
    void shouldReturn404WhenShipmentDoesNotExist() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();

        when(service.assignCourier(shipmentId, courierId))
            .thenThrow(new ResourceNotFoundException("Envío", shipmentId));

        mockMvc.perform(put(ENDPOINT, shipmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"courierId": "%s"}
                                """.formatted(courierId)))
            .andExpect(status().isNotFound());

        verify(service).assignCourier(shipmentId, courierId);
    }

    @Test
    void shouldReturn422WhenCourierIsInactive() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();

        when(service.assignCourier(shipmentId, courierId))
            .thenThrow(new CourierNotActiveException(
                "El repartidor no está activo: " + courierId));

        mockMvc.perform(put(ENDPOINT, shipmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"courierId": "%s"}
                                """.formatted(courierId)))
            .andExpect(status().isUnprocessableEntity());

        verify(service).assignCourier(shipmentId, courierId);
    }

    @Test
    void shouldReturn422WhenShipmentCannotBeAssigned() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        UUID courierId = UUID.randomUUID();

        when(service.assignCourier(shipmentId, courierId))
            .thenThrow(new BusinessRuleException(
                "Solo se puede asignar un repartidor a un envío pendiente"));

        mockMvc.perform(put(ENDPOINT, shipmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"courierId": "%s"}
                                """.formatted(courierId)))
            .andExpect(status().isUnprocessableEntity());

        verify(service).assignCourier(shipmentId, courierId);
    }

    @Test
    void shouldReturn400WhenCourierIdIsNull() throws Exception {
        UUID shipmentId = UUID.randomUUID();

        mockMvc.perform(put(ENDPOINT, shipmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"courierId": null}
                                """))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void shouldReturn400WhenCourierIdIsMissing() throws Exception {
        UUID shipmentId = UUID.randomUUID();

        mockMvc.perform(put(ENDPOINT, shipmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void shouldReturn400WhenShipmentIdIsNotAValidUuid() throws Exception {
        mockMvc.perform(put("/api/v1/shipments/no-es-uuid/courier")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"courierId": "%s"}
                                """.formatted(UUID.randomUUID())))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }
}
