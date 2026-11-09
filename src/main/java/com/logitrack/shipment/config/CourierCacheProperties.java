package com.logitrack.shipment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propiedades de configuración de la caché de disponibilidad de repartidores.
 *
 * <p>Corresponde al bloque {@code logitrack.cache.couriers-available} del
 * {@code application.yaml}. Los valores por defecto coinciden con la SDD 2.2.3
 * (TTL 5 min, prefijo {@code couriers::available}).</p>
 */
@ConfigurationProperties(prefix = "logitrack.cache.couriers-available")
public record CourierCacheProperties(
        @DefaultValue("300") long ttlSeconds,
        @DefaultValue("couriers::available") String keyPrefix) {
}
