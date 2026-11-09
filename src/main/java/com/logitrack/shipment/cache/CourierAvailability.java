package com.logitrack.shipment.cache;

import java.io.Serializable;
import java.util.UUID;

/**
 * Proyección inmutable de la disponibilidad de un repartidor, almacenada en Redis.
 *
 * <p>Representa el subconjunto de datos del repartidor relevante para la disponibilidad:
 * identificador, estado y disponibilidad. Se serializa como JSON en Redis.</p>
 *
 * <p>No depende de {@code client/} ni de {@code security/}: el acoplamiento es unidireccional
 * (caché → dominio, no al revés).</p>
 *
 * @param courierId identificador único del repartidor
 * @param status    estado del repartidor ({@code "ACTIVO"} o {@code "INACTIVO"})
 * @param available {@code true} si el repartidor está disponible para ser asignado
 */
public record CourierAvailability(UUID courierId, String status, Boolean available)
        implements Serializable {
}
