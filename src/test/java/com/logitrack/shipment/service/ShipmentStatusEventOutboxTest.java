package com.logitrack.shipment.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.entity.OutboxEvent;
import com.logitrack.shipment.entity.OutboxStatus;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.repository.OutboxEventRepository;
import com.logitrack.shipment.support.ShipmentEntities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ShipmentStatusEventOutboxTest {

    private static final Instant NOW = Instant.parse("2026-11-08T12:30:45.123Z");

    @Mock
    private OutboxEventRepository repository;

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void persistsImmutablePayloadWithExpectedContractAndCourier() throws Exception {
        Shipment shipment = ShipmentEntities.shipment();
        UUID courierId = UUID.randomUUID();
        shipment.assignCourier(courierId);
        var outbox = outbox();

        outbox.record(shipment, ShipmentStatus.PENDIENTE, ShipmentStatus.EN_RUTA);

        OutboxEvent saved = captureSaved();
        assertThat(saved.getEventId()).isEqualTo(UUID.fromString(saved.getEventId()).toString());
        assertThat(saved.getShipmentId()).isEqualTo(shipment.getId().toString());
        assertThat(saved.getOccurredAt()).isEqualTo(NOW);
        assertThat(saved.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(saved.getStatus()).isEqualTo(OutboxStatus.PENDIENTE);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getLockedAt()).isNull();
        assertThat(saved.getLockedBy()).isNull();

        JsonNode payload = json.readTree(saved.getPayload());
        assertThat(payload.get("eventId").asText()).isEqualTo(saved.getEventId());
        assertThat(payload.get("shipmentId").asText()).isEqualTo(shipment.getId().toString());
        assertThat(payload.get("trackingCode").asText()).isEqualTo(shipment.getTrackingCode());
        assertThat(payload.get("courierId").asText()).isEqualTo(courierId.toString());
        assertThat(payload.get("previousStatus").asText()).isEqualTo("PENDIENTE");
        assertThat(payload.get("currentStatus").asText()).isEqualTo("EN_RUTA");
        assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-11-08T12:30:45.123Z");
    }

    @Test
    void cancellationWithoutCourierPersistsNullCourierId() throws Exception {
        Shipment shipment = ShipmentEntities.shipment();
        var outbox = outbox();

        outbox.record(shipment, ShipmentStatus.PENDIENTE, ShipmentStatus.CANCELADO);

        JsonNode payload = json.readTree(captureSaved().getPayload());
        assertThat(payload.get("courierId").isNull()).isTrue();
        assertThat(payload.get("currentStatus").asText()).isEqualTo("CANCELADO");
    }

    @Test
    void refusesToPersistAnEventWithoutShipmentId() {
        Shipment shipment = ShipmentEntities.shipment();
        org.springframework.test.util.ReflectionTestUtils.setField(shipment, "id", null);

        assertThatThrownBy(() -> outbox().record(shipment, ShipmentStatus.PENDIENTE, ShipmentStatus.EN_RUTA))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shipmentId");
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private ShipmentStatusEventOutbox outbox() {
        return new ShipmentStatusEventOutbox(repository,
                new Jackson2JsonMessageConverter(json),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private OutboxEvent captureSaved() {
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
