package com.logitrack.shipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Evento de dominio persistido en la misma transacción que el cambio de estado del envío.
 *
 * <p>El payload se serializa una única vez al registrar el evento y se conserva inmutable
 * para todos los reintentos. La publicación se realiza en una transacción posterior.</p>
 */
@Getter
@Entity
@Table(name = "shipment_status_event_outbox",
        uniqueConstraints = @UniqueConstraint(name = "uk_outbox_event_id", columnNames = "event_id"),
        indexes = {
                @Index(name = "idx_outbox_pending", columnList = "status, next_attempt_at"),
                @Index(name = "idx_outbox_shipment_order", columnList = "shipment_id, id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, updatable = false, length = 36)
    private String eventId;

    @Column(name = "shipment_id", nullable = false, updatable = false, length = 36)
    private String shipmentId;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "locked_by", length = 100)
    private String lockedBy;

    @Column(name = "sent_at")
    private Instant sentAt;

    public OutboxEvent(String eventId, String shipmentId, String payload, Instant occurredAt,
                       Instant nextAttemptAt) {
        this.eventId = eventId;
        this.shipmentId = shipmentId;
        this.payload = payload;
        this.occurredAt = occurredAt;
        this.nextAttemptAt = nextAttemptAt;
        this.status = OutboxStatus.PENDIENTE;
        this.attempts = 0;
    }
}
