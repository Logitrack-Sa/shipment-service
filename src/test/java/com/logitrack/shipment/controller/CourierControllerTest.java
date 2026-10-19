package com.logitrack.shipment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.dto.CourierRequest;
import com.logitrack.shipment.dto.CourierResponse;
import com.logitrack.shipment.dto.CourierStatusRequest;
import com.logitrack.shipment.entity.CourierStatus;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.service.CourierService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CourierController.class)
class CourierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CourierService courierService;

    @Test
    void createCourierReturnsCreated() throws Exception {
        UUID courierId = UUID.randomUUID();
        CourierRequest request = new CourierRequest("Luis Pérez", "MOTO");
        CourierResponse response = new CourierResponse(courierId, "Luis Pérez", "MOTO", CourierStatus.ACTIVO, Instant.now());

        when(courierService.create(any(CourierRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/couriers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(courierId.toString()))
            .andExpect(jsonPath("$.name").value("Luis Pérez"))
            .andExpect(jsonPath("$.status").value("ACTIVO"));
    }

    @Test
    void getCourierByIdReturnsOk() throws Exception {
        UUID courierId = UUID.randomUUID();
        CourierResponse response = new CourierResponse(courierId, "Carlos Gómez", "AUTO", CourierStatus.ACTIVO, Instant.now());

        when(courierService.findById(courierId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/couriers/{id}", courierId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(courierId.toString()))
            .andExpect(jsonPath("$.name").value("Carlos Gómez"));
    }

    @Test
    void getCourierByIdReturnsNotFoundWhenNotExists() throws Exception {
        UUID courierId = UUID.randomUUID();

        when(courierService.findById(courierId)).thenThrow(new ResourceNotFoundException("Repartidor ", courierId));

        mockMvc.perform(get("/api/v1/couriers/{id}", courierId))
            .andExpect(status().isNotFound());
    }

    @Test
    void changeStatusReturnsOk() throws Exception {
        UUID courierId = UUID.randomUUID();
        CourierStatusRequest request = new CourierStatusRequest(CourierStatus.INACTIVO);
        CourierResponse response = new CourierResponse(courierId, "Luis Pérez", "MOTO", CourierStatus.INACTIVO, Instant.now());

        when(courierService.changeStatus(eq(courierId), eq(CourierStatus.INACTIVO))).thenReturn(response);

        mockMvc.perform(patch("/api/v1/couriers/{id}/status", courierId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("INACTIVO"));
    }
}
