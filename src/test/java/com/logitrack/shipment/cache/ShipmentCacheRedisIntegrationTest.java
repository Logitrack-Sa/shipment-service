package com.logitrack.shipment.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.client.CourierLookup;
import com.logitrack.shipment.dto.AssignCourierRequest;
import com.logitrack.shipment.dto.ChangeStatusRequest;
import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.CustomerRepository;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.repository.ShipmentStatusHistoryRepository;
import com.logitrack.shipment.support.JwtTestSupport;
import com.logitrack.shipment.support.ShipmentFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShipmentCacheRedisIntegrationTest extends JwtTestSupport {

    @Container
    static GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("spring.cache.type", () -> "redis");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ShipmentStatusHistoryRepository historyRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockitoBean
    private CourierLookup couriers;

    private Customer customerA;
    private Customer customerB;
    private Shipment shipmentA;

    @BeforeEach
    void setUp() {
        var connection = redisTemplate.getConnectionFactory().getConnection();
        connection.serverCommands().flushAll();
        connection.close();

        historyRepository.deleteAll();
        shipmentRepository.deleteAll();
        customerRepository.deleteAll();

        customerA = customerRepository.saveAndFlush(new Customer("Cliente A", "clienteA@test.com", "123456789"));
        customerB = customerRepository.saveAndFlush(new Customer("Cliente B", "clienteB@test.com", "987654321"));

        var entity = new ShipmentMapper().toEntity(ShipmentFixtures.request(), customerA, "LT-CACHE12345", new BigDecimal("2500.00"));
        shipmentA = shipmentRepository.saveAndFlush(entity);

        doNothing().when(couriers).requireAvailable(any());
        doNothing().when(couriers).requireActive(any());
    }

    private String bearer(String role, Map<String, Object> claims) {
        return "Bearer " + token(role, claims);
    }

    @Test
    @DisplayName("Cache-Aside: Primera consulta almacena en Redis y segunda es Cache Hit")
    void trackingCacheMissAndHit() throws Exception {
        String trackingKey = "shipment::tracking::" + shipmentA.getTrackingCode();
        assertThat(redisTemplate.hasKey(trackingKey)).isFalse();

        // 1. Cache Miss -> Consulta MySQL y almacena en Redis
        mvc.perform(get("/api/v1/shipments/tracking/" + shipmentA.getTrackingCode())
                        .header("Authorization", bearer("admin", Map.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value(shipmentA.getTrackingCode()));

        assertThat(redisTemplate.hasKey(trackingKey)).isTrue();
        Long ttl = redisTemplate.getExpire(trackingKey, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(500L).isLessThanOrEqualTo(600L);

        // 2. Cache Hit -> Responde desde Redis
        mvc.perform(get("/api/v1/shipments/tracking/" + shipmentA.getTrackingCode())
                        .header("Authorization", bearer("admin", Map.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value(shipmentA.getTrackingCode()));
    }

    @Test
    @DisplayName("Cache-Aside por ID: Almacena bajo la clave shipment::id::{id}")
    void idCacheMissAndHit() throws Exception {
        String idKey = "shipment::id::" + shipmentA.getId();
        assertThat(redisTemplate.hasKey(idKey)).isFalse();

        mvc.perform(get("/api/v1/shipments/" + shipmentA.getId())
                        .header("Authorization", bearer("admin", Map.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shipmentA.getId().toString()));

        assertThat(redisTemplate.hasKey(idKey)).isTrue();
        Long ttl = redisTemplate.getExpire(idKey, TimeUnit.SECONDS);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(500L).isLessThanOrEqualTo(600L);
    }

    @Test
    @DisplayName("Cache Evict: Al cambiar estado del envío se invalidan las claves de Redis")
    void evictOnChangeStatus() throws Exception {
        String trackingKey = "shipment::tracking::" + shipmentA.getTrackingCode();
        String idKey = "shipment::id::" + shipmentA.getId();

        UUID courierId = identityLink("repartidor-a", "courierId");
        shipmentA.assignCourier(courierId);
        shipmentRepository.saveAndFlush(shipmentA);

        // Pre-cargar en cache
        mvc.perform(get("/api/v1/shipments/tracking/" + shipmentA.getTrackingCode())
                .header("Authorization", bearer("admin", Map.of()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/shipments/" + shipmentA.getId())
                .header("Authorization", bearer("admin", Map.of()))).andExpect(status().isOk());

        assertThat(redisTemplate.hasKey(trackingKey)).isTrue();
        assertThat(redisTemplate.hasKey(idKey)).isTrue();

        // Mutar estado (PATCH /api/v1/shipments/{id}/status)
        var changeRequest = new ChangeStatusRequest(ShipmentStatus.EN_RUTA, "En camino");
        mvc.perform(patch("/api/v1/shipments/" + shipmentA.getId() + "/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(changeRequest))
                        .header("Authorization", bearer("admin", Map.of())))
                .andExpect(status().isOk());

        // Verificar que ambas claves fueron desalojadas de Redis
        assertThat(redisTemplate.hasKey(trackingKey)).isFalse();
        assertThat(redisTemplate.hasKey(idKey)).isFalse();
    }

    @Test
    @DisplayName("Cache Evict: Al asignar repartidor se invalidan las claves de Redis")
    void evictOnAssignCourier() throws Exception {
        String trackingKey = "shipment::tracking::" + shipmentA.getTrackingCode();
        String idKey = "shipment::id::" + shipmentA.getId();

        // Pre-cargar en cache
        mvc.perform(get("/api/v1/shipments/tracking/" + shipmentA.getTrackingCode())
                .header("Authorization", bearer("admin", Map.of()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/shipments/" + shipmentA.getId())
                .header("Authorization", bearer("admin", Map.of()))).andExpect(status().isOk());

        assertThat(redisTemplate.hasKey(trackingKey)).isTrue();
        assertThat(redisTemplate.hasKey(idKey)).isTrue();

        UUID newCourier = UUID.randomUUID();
        var assignRequest = new AssignCourierRequest(newCourier);
        mvc.perform(put("/api/v1/shipments/" + shipmentA.getId() + "/courier")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(assignRequest))
                        .header("Authorization", bearer("admin", Map.of())))
                .andExpect(status().isOk());

        assertThat(redisTemplate.hasKey(trackingKey)).isFalse();
        assertThat(redisTemplate.hasKey(idKey)).isFalse();
    }

    @Test
    @DisplayName("Cache Evict: Al cancelar envío se invalidan las claves de Redis")
    void evictOnCancel() throws Exception {
        String trackingKey = "shipment::tracking::" + shipmentA.getTrackingCode();
        String idKey = "shipment::id::" + shipmentA.getId();

        mvc.perform(get("/api/v1/shipments/tracking/" + shipmentA.getTrackingCode())
                .header("Authorization", bearer("admin", Map.of()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/shipments/" + shipmentA.getId())
                .header("Authorization", bearer("admin", Map.of()))).andExpect(status().isOk());

        assertThat(redisTemplate.hasKey(trackingKey)).isTrue();
        assertThat(redisTemplate.hasKey(idKey)).isTrue();

        mvc.perform(post("/api/v1/shipments/" + shipmentA.getId() + "/cancellation")
                        .header("Authorization", bearer("admin", Map.of())))
                .andExpect(status().isOk());

        assertThat(redisTemplate.hasKey(trackingKey)).isFalse();
        assertThat(redisTemplate.hasKey(idKey)).isFalse();
    }

    @Test
    @DisplayName("Seguridad: En Cache Hit un cliente ajeno recibe 403 Forbidden")
    void securityEnforcedOnCacheHit() throws Exception {
        String trackingKey = "shipment::tracking::" + shipmentA.getTrackingCode();

        // Cliente A (dueño) consulta -> Miss y almacena en Redis
        mvc.perform(get("/api/v1/shipments/tracking/" + shipmentA.getTrackingCode())
                        .header("Authorization", bearer("cliente-a", Map.of("customerId", customerA.getId().toString()))))
                .andExpect(status().isOk());

        assertThat(redisTemplate.hasKey(trackingKey)).isTrue();

        // Cliente B (no dueño) consulta el mismo tracking -> Cache Hit pero 403 Forbidden
        mvc.perform(get("/api/v1/shipments/tracking/" + shipmentA.getTrackingCode())
                        .header("Authorization", bearer("cliente-b", Map.of("customerId", customerB.getId().toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
