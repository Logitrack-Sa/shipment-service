package com.logitrack.shipment.entity;

/**
 * Estado de entrega de un evento persistido en el outbox transaccional.
 */
public enum OutboxStatus {
    /** Evento confirmado con el cambio de estado, pendiente de publicar en RabbitMQ. */
    PENDIENTE,
    /** Evento confirmado por el broker y marcado como entregado. */
    ENVIADO
}
