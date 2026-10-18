package com.logitrack.shipment.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.dto.CustomerRequest;
import com.logitrack.shipment.dto.CustomerResponse;
import com.logitrack.shipment.service.CustomerService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomerController.class)
class CustomerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; // Convierte objetos Java a JSON y viceversa

    @MockitoBean
    private CustomerService customerService; // Simulamos el servicio para aislar el controlador

    @Test
    void createCustomerReturnsCreatedWhenRequestIsValid() throws Exception {
        UUID id = UUID.randomUUID();
        CustomerRequest request = new CustomerRequest("Ana", "ana@mail.com", "+549388123456");
        CustomerResponse response = new CustomerResponse(id, "Ana", "ana@mail.com", "+549388123456", Instant.now());

        when(customerService.create(any(CustomerRequest.class))).thenReturn(response);

        // Simulamos la petición POST enviando un JSON
        mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated()) // Esperamos HTTP 201
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("Ana"))
            .andExpect(jsonPath("$.email").value("ana@mail.com"));
    }

    @Test
    void createCustomerFailsWhenEmailIsInvalid() throws Exception {
        // Un email claramente inválido para probar las anotaciones @Valid del DTO
        CustomerRequest request = new CustomerRequest("Ana", "correo-invalido", null);

        mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest()); // Esperamos HTTP 400 por error de validación
    }

    @Test
    void findByIdReturnsCustomerWhenExists() throws Exception {
        UUID id = UUID.randomUUID();
        CustomerResponse response = new CustomerResponse(id, "Ana", "ana@mail.com", null, Instant.now());

        when(customerService.findById(id)).thenReturn(response);

        // Simulamos la petición GET pasando el ID en la URL
        mockMvc.perform(get("/api/v1/customers/{id}", id))
            .andExpect(status().isOk()) // Esperamos HTTP 200
            .andExpect(jsonPath("$.id").value(id.toString()))
            .andExpect(jsonPath("$.name").value("Ana"));
    }
}
