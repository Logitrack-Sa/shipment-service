package com.logitrack.shipment.cache;

import com.logitrack.shipment.client.CourierFeignClient.CourierResult;
import com.logitrack.shipment.client.CourierLookup;
import com.logitrack.shipment.config.RedisCacheConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Servicio de caché de disponibilidad de repartidores (issue #108).
 *
 * <p>Envuelve la consulta a {@link CourierLookup} con Spring Cache respaldado por Redis.
 * La autorización <strong>no</strong> se gestiona aquí: las validaciones de
 * {@code JwtAuthenticationToken} y {@code ShipmentAccess} deben ejecutarse en el punto
 * de entrada del controlador o servicio <em>antes</em> de llamar a este servicio.
 * Un cache-hit nunca devuelve disponibilidad sin autenticación válida.</p>
 *
 * <p><strong>Regla de desacoplamiento:</strong> este servicio depende de {@link CourierLookup}
 * (interfaz de dominio) y de {@link CourierAvailability} (DTO de dominio/caché), pero
 * <em>no</em> del paquete {@code security/} directamente. La dependencia circular
 * está prohibida.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourierAvailabilityCacheService {

    private final CourierAvailabilityLoader loader;

    /**
     * Devuelve la disponibilidad del repartidor, sirviéndola desde la caché Redis si existe
     * (cache-hit), o consultando al {@link CourierLookup} en caso de miss.
     *
     * <p>La clave de caché sigue el patrón {@code couriers::available:{courierId}}.</p>
     *
     * @param courierId identificador del repartidor
     * @return disponibilidad del repartidor
     */
    @Cacheable(
            value = RedisCacheConfig.COURIERS_AVAILABLE,
            key = "'" + "couriers::available" + ":' + #courierId")
    public CourierAvailability getAvailability(UUID courierId) {
        log.debug("Cache MISS para repartidor {}: consultando courier-service", courierId);
        return loader.load(courierId);
    }

    /**
     * Invalida <em>todas</em> las entradas de la caché de repartidores disponibles.
     *
     * <p>Debe invocarse después de cualquier cambio de estado de un envío que libere u ocupe
     * un repartidor. El método es idempotente y seguro de llamar aunque la caché esté vacía.</p>
     */
    @CacheEvict(value = RedisCacheConfig.COURIERS_AVAILABLE, allEntries = true)
    public void evictAll() {
        log.info("Caché de disponibilidad de repartidores invalidada (allEntries=true)");
    }
}
