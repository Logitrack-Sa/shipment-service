package com.logitrack.shipment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.client.CourierLookup;
import com.logitrack.shipment.config.OutboxProperties;
import com.logitrack.shipment.config.RabbitMQConfig;
import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.entity.OutboxEvent;
import com.logitrack.shipment.entity.OutboxStatus;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.CustomerRepository;
import com.logitrack.shipment.repository.OutboxEventRepository;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.repository.ShipmentStatusHistoryRepository;
import com.logitrack.shipment.support.JwtTestSupport;
import com.logitrack.shipment.support.ShipmentFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShipmentStatusEventRabbitIntegrationTest extends JwtTestSupport {

    private static final String TEST_QUEUE = "test.courier.shipment-status.queue";
    private static final String ROUTING_KEY = RabbitMQConfig.SHIPMENT_STATUS_CHANGED_ROUTING_KEY;

    private static final RabbitMQContainer RABBIT =
            new RabbitMQContainer(DockerImageName.parse("rabbitmq:3.13-management-alpine"))
                    .withStartupTimeout(java.time.Duration.ofSeconds(240));

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.rabbitmq.connection-timeout", () -> "1000");
        registry.add("logitrack.outbox.enabled", () -> "true");
        registry.add("logitrack.outbox.publish-interval-ms", () -> "3600000");
        registry.add("logitrack.outbox.confirm-timeout-ms", () -> "3000");
        registry.add("logitrack.outbox.initial-retry-delay-ms", () -> "1");
        registry.add("logitrack.outbox.max-retry-delay-ms", () -> "1");
    }

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
    private ShipmentStatusEventPublisher publisher;
    @Autowired
    private OutboxProperties properties;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private AmqpAdmin amqpAdmin;
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
        customer = customers.saveAndFlush(new Customer("Rabbit", "rabbit@test.com", "123"));
        courierId = UUID.randomUUID();
        withCourier = seed("LT-RABBIT0001", courierId);
        withoutCourier = seed("LT-RABBIT0002", null);
        declareIsolatedQueue();
    }

    @Test
    @Order(1)
    void publishesJsonContractInOrderOnlyAfterCommit() throws Exception {
        changeStatus(withCourier, "EN_RUTA");
        changeStatus(withCourier, "ENTREGADO");

        assertThat(outbox.count()).isEqualTo(2);
        assertThat(receive(200)).isNull();

        assertThat(publisher.publishOnce()).isEqualTo(1);
        assertThat(publisher.publishOnce()).isEqualTo(1);

        Message first = receive(5000);
        Message second = receive(5000);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertContract(first, "PENDIENTE", "EN_RUTA", courierId.toString(), withCourier);
        assertContract(second, "EN_RUTA", "ENTREGADO", courierId.toString(), withCourier);
        assertThat(outbox.findAll()).extracting(OutboxEvent::getStatus)
                .containsOnly(OutboxStatus.ENVIADO);
    }

    @Test
    @Order(2)
    void cancellationEndpointPublishesCourierNullWhenThereIsNoCourier() throws Exception {
        cancel(withCourier);
        cancel(withoutCourier);

        assertThat(publisher.publishOnce()).isEqualTo(2);

        List<Message> messages = List.of(receive(5000), receive(5000));
        JsonNode with = byShipment(messages, withCourier.getId());
        JsonNode without = byShipment(messages, withoutCourier.getId());
        assertThat(with.get("currentStatus").asText()).isEqualTo("CANCELADO");
        assertThat(with.get("courierId").asText()).isEqualTo(courierId.toString());
        assertThat(without.get("currentStatus").asText()).isEqualTo("CANCELADO");
        assertThat(without.get("courierId").isNull()).isTrue();
    }

    @Test
    @Order(3)
    void returnedMessageIsNotDeliveredAndRecoversWithSameEventId() throws Exception {
        changeStatus(withCourier, "EN_RUTA");
        OutboxEvent pending = outbox.findAll().get(0);
        String eventId = pending.getEventId();

        amqpAdmin.deleteQueue(TEST_QUEUE);
        assertThat(publisher.publishOnce()).isZero();
        OutboxEvent failed = outbox.findById(pending.getId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(OutboxStatus.PENDIENTE);
        assertThat(failed.getAttempts()).isEqualTo(1);
        assertThat(failed.getEventId()).isEqualTo(eventId);

        declareIsolatedQueue();
        awaitBroker();
        assertThat(publisher.publishOnce()).isEqualTo(1);
        Message recovered = receive(5000);
        assertThat(recovered).isNotNull();
        JsonNode payload = payload(recovered);
        assertThat(payload.get("eventId").asText()).isEqualTo(eventId);
        assertThat(outbox.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.ENVIADO);
    }

    @Test
    @Order(4)
    void brokerOutageKeepsEventPendingAndDeliversSameEventIdAfterRecovery() throws Exception {
        changeStatus(withCourier, "EN_RUTA");
        OutboxEvent pending = outbox.findAll().get(0);
        String eventId = pending.getEventId();
        String containerId = RABBIT.getContainerId();

        RABBIT.getDockerClient().pauseContainerCmd(containerId).exec();
        try {
            assertThat(publisher.publishOnce()).isZero();
            OutboxEvent failed = outbox.findById(pending.getId()).orElseThrow();
            assertThat(failed.getStatus()).isEqualTo(OutboxStatus.PENDIENTE);
            assertThat(failed.getAttempts()).isEqualTo(1);
            assertThat(failed.getEventId()).isEqualTo(eventId);
        } finally {
            RABBIT.getDockerClient().unpauseContainerCmd(containerId).exec();
        }

        awaitBroker();
        publishUntilDelivered(pending.getId());
        Message recovered = receive(5000);
        assertThat(recovered).isNotNull();
        assertThat(payload(recovered).get("eventId").asText()).isEqualTo(eventId);
        assertThat(outbox.findById(pending.getId()).orElseThrow().getStatus())
                .isEqualTo(OutboxStatus.ENVIADO);
    }

    @Test
    @Order(5)
    void dispatchesByPersistentOrderEvenWhenOccurredAtGoesBackwards() throws Exception {
        Instant now = Instant.now().minusSeconds(30);
        OutboxEvent enRoute = outbox.saveAndFlush(new OutboxEvent(UUID.randomUUID().toString(),
                withCourier.getId().toString(), "{\"currentStatus\":\"EN_RUTA\"}",
                now.minusSeconds(10), now));
        OutboxEvent delivered = outbox.saveAndFlush(new OutboxEvent(UUID.randomUUID().toString(),
                withCourier.getId().toString(), "{\"currentStatus\":\"ENTREGADO\"}",
                now.minusSeconds(20), now));

        assertThat(delivered.getOccurredAt()).isBefore(enRoute.getOccurredAt());
        assertThat(publisher.publishOnce()).isEqualTo(1);
        assertThat(publisher.publishOnce()).isEqualTo(1);

        Message first = receive(5000);
        Message second = receive(5000);
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(payload(first).get("currentStatus").asText()).isEqualTo("EN_RUTA");
        assertThat(payload(second).get("currentStatus").asText()).isEqualTo("ENTREGADO");
    }

    @Test
    @Order(6)
    void concurrentPublishersDeliverAnEventExactlyOnce() throws Exception {
        changeStatus(withCourier, "EN_RUTA");
        ShipmentStatusEventPublisher second = new ShipmentStatusEventPublisher(outbox,
                rabbitTemplate, properties, Clock.systemUTC());
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> firstCall = executor.submit(() -> publishAfterBarrier(publisher, ready, start));
            Future<Integer> secondCall = executor.submit(() -> publishAfterBarrier(second, ready, start));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(firstCall.get(20, TimeUnit.SECONDS)
                    + secondCall.get(20, TimeUnit.SECONDS)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            second.close();
        }

        assertThat(receive(5000)).isNotNull();
        assertThat(receive(200)).isNull();
        assertThat(outbox.findAll()).extracting(OutboxEvent::getStatus)
                .containsOnly(OutboxStatus.ENVIADO);
    }

    private int publishAfterBarrier(ShipmentStatusEventPublisher target, CountDownLatch ready,
                                    CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        return target.publishOnce();
    }

    private void publishUntilDelivered(Long outboxId) throws InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            publisher.publishOnce();
            if (outbox.findById(outboxId).orElseThrow().getStatus() == OutboxStatus.ENVIADO) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("El evento no se entregó después de recuperar el broker");
    }

    private void awaitBroker() throws InterruptedException {
        for (int attempt = 0; attempt < 50; attempt++) {
            try {
                rabbitTemplate.execute(channel -> {
                    channel.queueDeclarePassive(TEST_QUEUE);
                    return null;
                });
                return;
            } catch (RuntimeException exception) {
                Thread.sleep(200);
            }
        }
        throw new AssertionError("El broker no se recuperó");
    }

    private void declareIsolatedQueue() {
        TopicExchange exchange = new TopicExchange(RabbitMQConfig.SHIPMENT_EXCHANGE, true, false);
        Queue queue = QueueBuilder.durable(TEST_QUEUE).build();
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY);
        amqpAdmin.declareExchange(exchange);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareBinding(binding);
        amqpAdmin.purgeQueue(TEST_QUEUE);
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
        mvc.perform(patch("/api/v1/shipments/" + shipment.getId() + "/status")
                        .header("Authorization", "Bearer " + token("admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + target + "\"}"))
                .andExpect(status().isOk());
    }

    private void cancel(Shipment shipment) throws Exception {
        mvc.perform(post("/api/v1/shipments/" + shipment.getId() + "/cancellation")
                        .header("Authorization", "Bearer " + token("admin")))
                .andExpect(status().isOk());
    }

    private Message receive(long timeoutMs) {
        return rabbitTemplate.receive(TEST_QUEUE, timeoutMs);
    }

    private JsonNode payload(Message message) throws Exception {
        return json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
    }

    private JsonNode byShipment(List<Message> messages, UUID shipmentId) throws Exception {
        return messages.stream()
                .map(message -> {
                    try {
                        return payload(message);
                    } catch (Exception exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .filter(payload -> payload.get("shipmentId").asText().equals(shipmentId.toString()))
                .findFirst()
                .orElseThrow();
    }

    private void assertContract(Message message, String previous, String current, String courier,
                                Shipment shipment) throws Exception {
        assertThat(message.getMessageProperties().getContentType())
                .isEqualTo("application/json");
        assertThat(message.getMessageProperties().getReceivedExchange())
                .isEqualTo(RabbitMQConfig.SHIPMENT_EXCHANGE);
        assertThat(message.getMessageProperties().getReceivedRoutingKey()).isEqualTo(ROUTING_KEY);

        JsonNode payload = payload(message);
        assertThat(UUID.fromString(payload.get("eventId").asText())).isNotNull();
        assertThat(payload.get("shipmentId").asText()).isEqualTo(shipment.getId().toString());
        assertThat(UUID.fromString(payload.get("shipmentId").asText())).isNotNull();
        assertThat(payload.get("trackingCode").asText()).isEqualTo(shipment.getTrackingCode());
        assertThat(payload.get("courierId").asText()).isEqualTo(courier);
        assertThat(payload.get("previousStatus").asText()).isEqualTo(previous);
        assertThat(payload.get("currentStatus").asText()).isEqualTo(current);
        Instant occurredAt = Instant.parse(payload.get("occurredAt").asText());
        assertThat(occurredAt).isNotNull();
        assertThat(payload.get("occurredAt").asText()).endsWith("Z");
    }
}
