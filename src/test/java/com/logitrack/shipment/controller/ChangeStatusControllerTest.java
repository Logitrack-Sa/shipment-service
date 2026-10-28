package com.logitrack.shipment.controller;

import com.logitrack.shipment.dto.ShipmentResponse;import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.exception.CourierNotAssignedException;
import com.logitrack.shipment.exception.InvalidStateTransitionException;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de la capa web para {@code PATCH /api/v1/shipments/{id}/status}: respuestas 200, 400,
 * 404 y 422 según la transición y la validez de la solicitud.
 */
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.ActiveProfiles("test")
@WebMvcTest(ShipmentController.class)
class ChangeStatusControllerTest {

    private static final String ENDPOINT = "/api/v1/shipments/{id}/status";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ShipmentService service;

    @Test
    void returns200WithUpdatedShipment() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        when(service.changeStatus(eq(shipmentId), eq(ShipmentStatus.EN_RUTA), eq("Retirado")))
                .thenReturn(response(shipmentId, ShipmentStatus.EN_RUTA));

        mockMvc.perform(patch(ENDPOINT, shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "EN_RUTA", "observation": "Retirado"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(shipmentId.toString()))
                .andExpect(jsonPath("$.status").value("EN_RUTA"));

        verify(service).changeStatus(shipmentId, ShipmentStatus.EN_RUTA, "Retirado");
    }

    @Test
    void acceptsMissingObservation() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        when(service.changeStatus(eq(shipmentId), eq(ShipmentStatus.CANCELADO), isNull()))
                .thenReturn(response(shipmentId, ShipmentStatus.CANCELADO));

        mockMvc.perform(patch(ENDPOINT, shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CANCELADO"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"));

        verify(service).changeStatus(shipmentId, ShipmentStatus.CANCELADO, null);
    }

    @Test
    void returns400WhenStatusIsMissing() throws Exception {
        mockMvc.perform(patch(ENDPOINT, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verifyNoInteractions(service);
    }

    @Test
    void returns400WhenStatusIsInvalidJsonValue() throws Exception {
        mockMvc.perform(patch(ENDPOINT, UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "INVALIDO"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));

        verifyNoInteractions(service);
    }

    @Test
    void returns400WhenShipmentIdIsNotAValidUuid() throws Exception {
        mockMvc.perform(patch("/api/v1/shipments/no-es-uuid/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "EN_RUTA"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void returns404WhenShipmentDoesNotExist() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        when(service.changeStatus(eq(shipmentId), any(), any()))
                .thenThrow(new ResourceNotFoundException("Envío", shipmentId));

        mockMvc.perform(patch(ENDPOINT, shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CANCELADO"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void returns422WhenTransitionIsInvalid() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        when(service.changeStatus(eq(shipmentId), any(), any()))
                .thenThrow(new InvalidStateTransitionException("Envío", "ENTREGADO", "CANCELADO"));

        mockMvc.perform(patch(ENDPOINT, shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "CANCELADO"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
    }

    @Test
    void returns422WhenCourierIsNotAssigned() throws Exception {
        UUID shipmentId = UUID.randomUUID();
        when(service.changeStatus(eq(shipmentId), any(), any()))
                .thenThrow(new CourierNotAssignedException(shipmentId));

        mockMvc.perform(patch(ENDPOINT, shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status": "EN_RUTA"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("COURIER_NOT_ASSIGNED"));
    }

    private ShipmentResponse response(UUID shipmentId, ShipmentStatus status) {
        Instant now = Instant.now();
        return new ShipmentResponse(shipmentId, "LT-A1B2C3D4E5", UUID.randomUUID(), null, null,
                new BigDecimal("2.000"), new BigDecimal("10.00"), new BigDecimal("15.00"),
                new BigDecimal("20.00"), new BigDecimal("25.00"), status, null, now, now, "system", "system", 0L);
    }
}
