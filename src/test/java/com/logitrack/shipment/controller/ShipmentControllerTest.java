package com.logitrack.shipment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.service.ShipmentService;
import com.logitrack.shipment.support.ShipmentFixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ShipmentController.class)
class ShipmentControllerTest {
    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @MockitoBean
    private ShipmentService service;

    @Test
    void createsShipmentWithLocationAndComputedFee() throws Exception {
        when(service.createShipment(any())).thenReturn(ShipmentFixtures.response());

        mvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(ShipmentFixtures.request())))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/shipments/" + ShipmentFixtures.SHIPMENT_ID))
                .andExpect(jsonPath("$.status").value("PENDIENTE"))
                .andExpect(jsonPath("$.trackingCode").value(ShipmentFixtures.TRACKING_CODE))
                .andExpect(jsonPath("$.fee").value(2200.0))
                .andExpect(jsonPath("$.courierId").isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"weightKg,0", "weightKg,-1", "widthCm,0", "heightCm,-1", "lengthCm,0"})
    void rejectsInvalidMeasurements(String field, String value) throws Exception {
        ObjectNode request = json.valueToTree(ShipmentFixtures.request());
        request.put(field, new java.math.BigDecimal(value));

        mvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.details[0].field").value(field));
        verifyNoInteractions(service);
    }

    @Test
    void validatesNestedAddress() throws Exception {
        ObjectNode request = json.valueToTree(ShipmentFixtures.request());
        ((ObjectNode) request.get("origin")).put("city", " ");

        mvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON).content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("origin.city"));
        verifyNoInteractions(service);
    }

    @Test
    void returnsStructuredNotFoundForUnknownCustomer() throws Exception {
        when(service.createShipment(any())).thenThrow(new ResourceNotFoundException("Cliente", ShipmentFixtures.CUSTOMER_ID));

        mvc.perform(post("/api/v1/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(ShipmentFixtures.request())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.path").value("/api/v1/shipments"));
    }
}
