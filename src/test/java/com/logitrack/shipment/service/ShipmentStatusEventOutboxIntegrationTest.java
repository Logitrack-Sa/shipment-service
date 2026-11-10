package com.logitrack.shipment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.client.CourierLookup;
import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.entity.OutboxEvent;
import com.logitrack.shipment.entity.OutboxStatus;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.entity.ShipmentStatusHistory;
import com.logitrack.shipment.exception.CourierNotActiveException;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.CustomerRepository;
import com.logitrack.shipment.repository.OutboxEventRepository;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.repository.ShipmentStatusHistoryRepository;
import com.logitrack.shipment.support.JwtTestSupport;
import com.logitrack.shipment.support.ShipmentFixtures;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ShipmentStatusEventOutboxIntegrationTest extends JwtTestSupport {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private ShipmentRepository shipments;
    @Autowired
    private ShipmentStatusHistoryRepository history;
    @Autowired
    private OutboxEventRepository outbox;
    @Autowired
    private ShipmentService service;
    @Autowired
    private PlatformTransactionManager transactions;
    @Autowired
    private EntityManager entityManager;
    @MockitoBean
    private CourierLookup couriers;

    private Customer customer;
    private Shipment withCourier;
    private Shipment withoutCourier;
    private UUID courierId;

    @BeforeEach
    void setUp() {
        history.deleteAll();
        outbox.deleteAll();
        shipments.deleteAll();
        customers.deleteAll();
        customer = customers.saveAndFlush(new Customer("Outbox", "outbox@test.com", "123"));
        courierId = UUID.randomUUID();
        withCourier = seed("LT-OUTBOX0001", courierId);
        withoutCourier = seed("LT-OUTBOX0002", null);
        history.saveAndFlush(new ShipmentStatusHistory(withCourier, null, ShipmentStatus.PENDIENTE,
                Instant.now(), "Alta"));
        history.saveAndFlush(new ShipmentStatusHistory(withoutCourier, null, ShipmentStatus.PENDIENTE,
                Instant.now(), "Alta"));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void enRouteAndDeliveredCreateExactlyOneOutboxEventPerTransition() throws Exception {
        changeStatus(withCourier, "EN_RUTA");
        List<OutboxEvent> first = orderedEvents();
        assertThat(first).hasSize(1);
        assertPayload(first.get(0), "PENDIENTE", "EN_RUTA", courierId.toString());

        changeStatus(withCourier, "ENTREGADO");
        List<OutboxEvent> second = orderedEvents();
        assertThat(second).hasSize(2);
        assertPayload(second.get(1), "EN_RUTA", "ENTREGADO", courierId.toString());
        assertThat(second.get(1).getEventId()).isNotEqualTo(second.get(0).getEventId());
    }

    @Test
    void cancellationEndpointCreatesEventWithAndWithoutCourier() throws Exception {
        cancel(withCourier);
        cancel(withoutCourier);

        List<OutboxEvent> events = orderedEvents();
        assertThat(events).hasSize(2);
        OutboxEvent with = eventFor(withCourier.getId());
        OutboxEvent without = eventFor(withoutCourier.getId());
        assertPayload(with, "PENDIENTE", "CANCELADO", courierId.toString());
        assertPayload(without, "PENDIENTE", "CANCELADO", null);
        assertThat(shipments.findById(withCourier.getId()).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.CANCELADO);
    }

    @Test
    void rejectedOperationsDoNotCreateOutboxEvents() throws Exception {
        changeStatusExpecting(withCourier, "ENTREGADO", 422);
        cancelExpecting(withCourier, "Bearer " + token("cliente-a"), 403);
        doThrow(new CourierNotActiveException(courierId)).when(couriers).requireActive(courierId);
        changeStatusExpecting(withCourier, "EN_RUTA", 422);
        assertThat(outbox.count()).isZero();

        cancel(withCourier);
        assertThat(outbox.count()).isEqualTo(1);
        cancelExpecting(withCourier, admin(), 422);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void outboxEventIsInvisibleToAnotherTransactionUntilCommit() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            authenticateAdmin();
            service.changeStatus(withCourier.getId(), ShipmentStatus.EN_RUTA, null);
            entityManager.flush();

            assertThat(outbox.count()).isEqualTo(1);
            assertThat(countInIndependentTransaction()).isZero();
        });

        assertThat(outbox.count()).isEqualTo(1);
        assertThat(shipments.findById(withCourier.getId()).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.EN_RUTA);
    }

    @Test
    void rollbackAfterFlushLeavesNoStateHistoryOrOutbox() {
        long historyBefore = history.count();
        TransactionTemplate transaction = new TransactionTemplate(transactions);

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            authenticateAdmin();
            service.changeStatus(withCourier.getId(), ShipmentStatus.EN_RUTA, null);
            entityManager.flush();

            assertThat(outbox.count()).isEqualTo(1);
            throw new IllegalStateException("Fallo después de escribir el outbox");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(shipments.findById(withCourier.getId()).orElseThrow().getStatus())
                .isEqualTo(ShipmentStatus.PENDIENTE);
        assertThat(history.count()).isEqualTo(historyBefore);
        assertThat(outbox.count()).isZero();
    }

    @Test
    void publishesOnlyTheOldestPendingEventOfAShipmentByPersistentId() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        OutboxEvent first = outbox.saveAndFlush(event(now.minusSeconds(10), now));
        OutboxEvent second = outbox.saveAndFlush(event(now.minusSeconds(20), now));

        assertThat(first.getOccurredAt()).isAfter(second.getOccurredAt());
        assertThat(outbox.findPublishableIds(now.plusSeconds(1), now.minusSeconds(30),
                OutboxStatus.PENDIENTE, PageRequest.of(0, 10)))
                .containsExactly(first.getId());

        assertThat(outbox.claim(first.getId(), "token", now, now.minusSeconds(30),
                OutboxStatus.PENDIENTE)).isEqualTo(1);
        assertThat(outbox.markSent(first.getId(), "token", now, OutboxStatus.ENVIADO,
                OutboxStatus.PENDIENTE)).isEqualTo(1);

        assertThat(outbox.findPublishableIds(now.plusSeconds(1), now.minusSeconds(30),
                OutboxStatus.PENDIENTE, PageRequest.of(0, 10)))
                .containsExactly(second.getId());
    }

    @Test
    void claimIsExclusiveAndExpiredLeaseIsRecoverable() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        OutboxEvent event = outbox.saveAndFlush(event(now, now));
        Instant leaseBefore = now.minusSeconds(30);

        assertThat(outbox.claim(event.getId(), "owner-a", now, leaseBefore, OutboxStatus.PENDIENTE))
                .isEqualTo(1);
        assertThat(outbox.claim(event.getId(), "owner-b", now, leaseBefore, OutboxStatus.PENDIENTE))
                .isZero();
        assertThat(outbox.markFailed(event.getId(), "owner-b", now, OutboxStatus.PENDIENTE)).isZero();
        assertThat(outbox.markSent(event.getId(), "owner-b", now, OutboxStatus.ENVIADO,
                OutboxStatus.PENDIENTE)).isZero();

        Instant later = now.plusMillis(10);
        assertThat(outbox.claim(event.getId(), "owner-b", later, later.minusMillis(1),
                OutboxStatus.PENDIENTE)).isEqualTo(1);
        assertThat(outbox.findById(event.getId()).orElseThrow().getLockedBy()).isEqualTo("owner-b");
    }

    private OutboxEvent event(Instant occurredAt, Instant nextAttemptAt) {
        return new OutboxEvent(UUID.randomUUID().toString(), withCourier.getId().toString(), "{}",
                occurredAt, nextAttemptAt);
    }

    private long countInIndependentTransaction() {
        TransactionTemplate independent = new TransactionTemplate(transactions);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return independent.execute(status -> outbox.count());
    }

    private void authenticateAdmin() {
        Jwt jwt = Jwt.withTokenValue("test-admin")
                .header("alg", "none")
                .subject("admin")
                .claim("roles", List.of("ADMIN"))
                .claim("email_verified", true)
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")), "admin"));
    }

    private Shipment seed(String trackingCode, UUID assignedCourier) {
        var shipment = new ShipmentMapper().toEntity(ShipmentFixtures.request(), customer, trackingCode,
                new BigDecimal("2200.00"));
        if (assignedCourier != null) {
            shipment.assignCourier(assignedCourier);
        }
        return shipments.saveAndFlush(shipment);
    }

    private void changeStatus(Shipment shipment, String target) throws Exception {
        changeStatusExpecting(shipment, target, 200);
    }

    private void changeStatusExpecting(Shipment shipment, String target, int expected) throws Exception {
        mvc.perform(patch("/api/v1/shipments/" + shipment.getId() + "/status")
                        .header("Authorization", admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().is(expected));
    }

    private void cancel(Shipment shipment) throws Exception {
        cancelExpecting(shipment, admin(), 200);
    }

    private void cancelExpecting(Shipment shipment, String authorization, int expected) throws Exception {
        mvc.perform(post("/api/v1/shipments/" + shipment.getId() + "/cancellation")
                        .header("Authorization", authorization))
                .andExpect(status().is(expected));
    }

    private String admin() {
        return "Bearer " + token("admin");
    }

    private List<OutboxEvent> orderedEvents() {
        return outbox.findAll().stream()
                .sorted(java.util.Comparator.comparing(OutboxEvent::getId))
                .toList();
    }

    private OutboxEvent eventFor(UUID shipmentId) {
        return outbox.findAll().stream()
                .filter(event -> event.getShipmentId().equals(shipmentId.toString()))
                .findFirst()
                .orElseThrow();
    }

    private void assertPayload(OutboxEvent event, String previous, String current, String courier)
            throws Exception {
        JsonNode payload = json.readTree(event.getPayload());
        assertThat(payload.get("shipmentId").asText()).isEqualTo(event.getShipmentId());
        assertThat(payload.get("eventId").asText()).isEqualTo(event.getEventId());
        assertThat(payload.get("previousStatus").asText()).isEqualTo(previous);
        assertThat(payload.get("currentStatus").asText()).isEqualTo(current);
        if (courier == null) {
            assertThat(payload.get("courierId").isNull()).isTrue();
        } else {
            assertThat(payload.get("courierId").asText()).isEqualTo(courier);
        }
        assertThat(UUID.fromString(payload.get("shipmentId").asText())).isNotNull();
        assertThat(payload.get("occurredAt").asText()).endsWith("Z");
    }
}
