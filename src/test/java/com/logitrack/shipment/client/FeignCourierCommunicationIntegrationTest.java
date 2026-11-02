package com.logitrack.shipment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.logitrack.shipment.dto.CreateShipmentRequest;
import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.repository.CustomerRepository;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.repository.ShipmentStatusHistoryRepository;
import com.logitrack.shipment.service.ShipmentService;
import com.logitrack.shipment.support.JwtTestSupport;
import com.logitrack.shipment.support.ShipmentFixtures;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(FeignCourierCommunicationIntegrationTest.LoadBalancerTestConfig.class)
class FeignCourierCommunicationIntegrationTest extends JwtTestSupport {

    private static final HttpServer COURIER_SERVER;
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final AtomicInteger TARGET_PORT = new AtomicInteger();
    private static final AtomicInteger MOCK_STATUS = new AtomicInteger(200);
    private static final AtomicReference<String> MOCK_BODY = new AtomicReference<>("");
    private static final AtomicLong MOCK_DELAY_MS = new AtomicLong(0);
    private static final AtomicReference<String> LAST_PATH = new AtomicReference<>();
    private static final AtomicReference<String> LAST_AUTH = new AtomicReference<>();

    static {
        try {
            COURIER_SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            COURIER_SERVER.setExecutor(EXECUTOR);
            COURIER_SERVER.createContext("/", exchange -> {
                LAST_PATH.set(exchange.getRequestURI().getPath());
                LAST_AUTH.set(exchange.getRequestHeaders().getFirst("Authorization"));
                try {
                    long pause = MOCK_DELAY_MS.get();
                    if (pause > 0) {
                        Thread.sleep(pause);
                    }
                    byte[] payload = MOCK_BODY.get().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(MOCK_STATUS.get(), payload.length);
                    exchange.getResponseBody().write(payload);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            COURIER_SERVER.start();
            TARGET_PORT.set(COURIER_SERVER.getAddress().getPort());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    @AfterAll
    static void stopServer() {
        COURIER_SERVER.stop(0);
        EXECUTOR.shutdownNow();
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShipmentService shipmentService;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ShipmentStatusHistoryRepository historyRepository;

    @Autowired
    private CourierProperties courierProperties;

    @Autowired
    private JwtDecoder jwtDecoder;

    private UUID customerId;
    private final UUID courierId = identityLink("repartidor-a", "courierId");

    @BeforeEach
    void setUp() {
        historyRepository.deleteAll();
        shipmentRepository.deleteAll();
        customerRepository.deleteAll();

        TARGET_PORT.set(COURIER_SERVER.getAddress().getPort());
        MOCK_STATUS.set(200);
        MOCK_DELAY_MS.set(0);
        LAST_PATH.set(null);
        LAST_AUTH.set(null);
        MOCK_BODY.set("""
                {"courierId":"%s","status":"ACTIVO","available":true}
                """.formatted(courierId));

        customerId = customerRepository
                .saveAndFlush(new Customer("Cliente Feign", "feign@logitrack.test", "+5493884000000"))
                .getId();
    }

    @Test
    void doesNotDependOnFixedBaseUrlAndUsesLogicalServiceId() {
        assertThat(courierProperties.baseUrl()).isNull();
        assertThat(courierProperties.serviceId()).isEqualTo("courier-service");
        assertThat(courierProperties.isValid()).isTrue();
    }

    @Test
    void assignsShipmentWhenCourierIsAvailableViaFeignAndServiceDiscovery() throws Exception {
        UUID shipmentId = createPendingShipment();
        String adminToken = token("admin");

        mockMvc.perform(put("/api/v1/shipments/{id}/courier", shipmentId)
                        .header(AUTHORIZATION, "Bearer " + adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"courierId":"%s"}
                                """.formatted(courierId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shipmentId.toString()))
                .andExpect(jsonPath("$.courierId").value(courierId.toString()))
                .andExpect(jsonPath("$.status").value("PENDIENTE"));

        assertThat(LAST_PATH.get()).isEqualTo("/api/v1/couriers/" + courierId + "/availability");
        assertThat(LAST_AUTH.get()).isEqualTo("Bearer " + adminToken);
        assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getCourierId()).isEqualTo(courierId);
    }

    @Test
    void queriesCourierStatusAndTransitionsShipmentToEnRutaSuccessfully() throws Exception {
        UUID shipmentId = createPendingShipment();
        authenticateProgrammatically("admin");
        shipmentService.assignCourier(shipmentId, courierId);
        SecurityContextHolder.clearContext();

        MOCK_BODY.set("""
                {"courierId":"%s","status":"ACTIVO"}
                """.formatted(courierId));
        String courierToken = token("repartidor-a");

        mockMvc.perform(patch("/api/v1/shipments/{id}/status", shipmentId)
                        .header(AUTHORIZATION, "Bearer " + courierToken)
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"status":"EN_RUTA","observation":"Salida a reparto"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shipmentId.toString()))
                .andExpect(jsonPath("$.status").value("EN_RUTA"))
                .andExpect(jsonPath("$.courierId").value(courierId.toString()));

        assertThat(LAST_PATH.get()).isEqualTo("/api/v1/internal/couriers/" + courierId + "/status");
        assertThat(LAST_AUTH.get()).isEqualTo("Bearer " + courierToken);
        assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getStatus()).isEqualTo(ShipmentStatus.EN_RUTA);
    }

    @Test
    void returnsControlled404WhenCourierDoesNotExistAndDoesNotAssign() throws Exception {
        UUID shipmentId = createPendingShipment();
        UUID unknownCourierId = UUID.fromString("20000000-0000-0000-0000-000000000099");
        MOCK_STATUS.set(404);
        MOCK_BODY.set("""
                {"code":"RESOURCE_NOT_FOUND","message":"Repartidor no encontrado"}
                """);

        mockMvc.perform(put("/api/v1/shipments/{id}/courier", shipmentId)
                        .header(AUTHORIZATION, "Bearer " + token("admin"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"courierId":"%s"}
                                """.formatted(unknownCourierId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Repartidor no encontrado: " + unknownCourierId));

        assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getCourierId()).isNull();
    }

    @Test
    void returnsControlled503WhenCourierServiceRespondsHttp500AndPreservesState() throws Exception {
        UUID shipmentId = createPendingShipment();
        MOCK_STATUS.set(500);
        MOCK_BODY.set("""
                {"code":"INTERNAL_SERVER_ERROR","message":"Fallo interno en courier-service"}
                """);

        mockMvc.perform(put("/api/v1/shipments/{id}/courier", shipmentId)
                        .header(AUTHORIZATION, "Bearer " + token("admin"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"courierId":"%s"}
                                """.formatted(courierId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("COURIER_UNAVAILABLE"));

        assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getCourierId()).isNull();
    }

    @Test
    void activatesFallbackAndReturnsControlled503WhenCourierServiceIsStoppedOrTimesOut() throws Exception {
        UUID shipmentId = createPendingShipment();

        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        TARGET_PORT.set(closedPort);

        mockMvc.perform(put("/api/v1/shipments/{id}/courier", shipmentId)
                        .header(AUTHORIZATION, "Bearer " + token("admin"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"courierId":"%s"}
                                """.formatted(courierId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("COURIER_UNAVAILABLE"));

        assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getCourierId()).isNull();

        TARGET_PORT.set(COURIER_SERVER.getAddress().getPort());
        MOCK_DELAY_MS.set(900);

        mockMvc.perform(put("/api/v1/shipments/{id}/courier", shipmentId)
                        .header(AUTHORIZATION, "Bearer " + token("admin"))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"courierId":"%s"}
                                """.formatted(courierId)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.code").value("COURIER_UNAVAILABLE"));

        assertThat(shipmentRepository.findById(shipmentId).orElseThrow().getCourierId()).isNull();
    }

    private UUID createPendingShipment() {
        authenticateProgrammatically("admin");
        try {
            var fixture = ShipmentFixtures.request();
            var request = new CreateShipmentRequest(
                    customerId,
                    fixture.origin(),
                    fixture.destination(),
                    new BigDecimal("2.5"),
                    new BigDecimal("30"),
                    new BigDecimal("20"),
                    new BigDecimal("40"));
            return shipmentService.createShipment(request).id();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private void authenticateProgrammatically(String identity) {
        var converter = new JwtGrantedAuthoritiesConverter();
        converter.setAuthoritiesClaimName("roles");
        converter.setAuthorityPrefix("ROLE_");
        var jwt = jwtDecoder.decode(token(identity, Map.of()));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, converter.convert(jwt)));
    }

    @TestConfiguration
    @LoadBalancerClient(name = "courier-service", configuration = DynamicEurekaSupplierConfig.class)
    static class LoadBalancerTestConfig {
    }

    static class DynamicEurekaSupplierConfig {

        @Bean
        ServiceInstanceListSupplier courierServiceInstanceListSupplier() {
            return new ServiceInstanceListSupplier() {
                @Override
                public String getServiceId() {
                    return "courier-service";
                }

                @Override
                public Flux<List<ServiceInstance>> get() {
                    return Flux.just(List.of(new DefaultServiceInstance(
                            "courier-instance-test",
                            "courier-service",
                            "127.0.0.1",
                            TARGET_PORT.get(),
                            false)));
                }
            };
        }
    }
}
