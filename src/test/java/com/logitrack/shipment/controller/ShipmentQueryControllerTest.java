package com.logitrack.shipment.controller;

import com.logitrack.shipment.dto.PageResponse;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.service.ShipmentService;
import com.logitrack.shipment.support.ShipmentFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc(addFilters = false)
@org.springframework.test.context.ActiveProfiles("test")
@WebMvcTest(ShipmentController.class)
class ShipmentQueryControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private ShipmentService service;

    @Test
    void findsById() throws Exception {
        when(service.findById(ShipmentFixtures.SHIPMENT_ID)).thenReturn(ShipmentFixtures.response());

        mvc.perform(get("/api/v1/shipments/{id}", ShipmentFixtures.SHIPMENT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ShipmentFixtures.SHIPMENT_ID.toString()));
    }

    @Test
    void findsByTrackingCode() throws Exception {
        when(service.findByTrackingCode(ShipmentFixtures.TRACKING_CODE)).thenReturn(ShipmentFixtures.response());

        mvc.perform(get("/api/v1/shipments/tracking/{code}", ShipmentFixtures.TRACKING_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value(ShipmentFixtures.TRACKING_CODE));
    }

    @Test
    void listsFilteredPageWithoutExposingSpringPage() throws Exception {
        when(service.findAll(eq(ShipmentStatus.PENDIENTE), any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(ShipmentFixtures.response()), 0, 20, 1, 1));

        mvc.perform(get("/api/v1/shipments").param("status", "PENDIENTE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDIENTE"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    @Test
    void listsWithoutStatusFilter() throws Exception {
        when(service.findAll(isNull(), any(Pageable.class))).thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/v1/shipments"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    void returnsStructured404() throws Exception {
        when(service.findById(ShipmentFixtures.SHIPMENT_ID))
                .thenThrow(new ResourceNotFoundException("Envío", ShipmentFixtures.SHIPMENT_ID));

        mvc.perform(get("/api/v1/shipments/{id}", ShipmentFixtures.SHIPMENT_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/api/v1/shipments/not-a-uuid",
            "/api/v1/shipments?status=INVALIDO",
            "/api/v1/shipments?page=-1",
            "/api/v1/shipments?size=0",
            "/api/v1/shipments?size=101"
    })
    void rejectsInvalidParameters(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        verifyNoInteractions(service);
    }
}
