package com.logitrack.shipment.service;

import com.logitrack.shipment.dto.ShipmentStatusChangedEvent;
import com.logitrack.shipment.entity.OutboxEvent;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Persiste la intención de publicar un evento de cambio de estado en la misma transacción que
 * el envío y su historial.
 *
 * <p>La serialización se realiza una única vez mediante el {@link MessageConverter} configurado
 * (Jackson) y el payload queda inmutable para todos los reintentos. El envío a RabbitMQ nunca
 * ocurre dentro de esta transacción: lo realiza {@link ShipmentStatusEventPublisher}.</p>
 */
@Service
@RequiredArgsConstructor
public class ShipmentStatusEventOutbox {

    private final OutboxEventRepository outbox;
    private final MessageConverter messageConverter;
    private final Clock clock;

    /**
     * Registra un evento para una transición ya validada.
     *
     * @param shipment envío persistido cuyo estado cambió
     * @param from     estado anterior
     * @param to       estado nuevo
     * @throws IllegalStateException si el envío no tiene identificador o no puede serializarse
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Shipment shipment, ShipmentStatus from, ShipmentStatus to) {
        if (shipment.getId() == null) {
            throw new IllegalStateException("No se puede registrar un evento sin shipmentId");
        }
        Instant occurredAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ShipmentStatusChangedEvent event = new ShipmentStatusChangedEvent(
                UUID.randomUUID(),
                shipment.getId().toString(),
                shipment.getTrackingCode(),
                shipment.getCourierId() != null ? shipment.getCourierId().toString() : null,
                from,
                to,
                occurredAt);
        Message message = messageConverter.toMessage(event, new MessageProperties());
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        outbox.save(new OutboxEvent(event.eventId().toString(), event.shipmentId(), payload,
                occurredAt, occurredAt));
    }
}
