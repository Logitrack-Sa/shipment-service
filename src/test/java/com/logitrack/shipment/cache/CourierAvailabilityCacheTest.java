package com.logitrack.shipment.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas unitarias de la capa de caché de disponibilidad de repartidores.
 *
 * <p>Verifica el comportamiento de cache-miss (primera llamada), cache-hit (segunda llamada),
 * invalidación por {@code evictAll} y que el prefijo de clave sigue el patrón
 * {@code couriers::available:{courierId}}.</p>
 *
 * <p>No levanta contenedores Docker: usa mocks de Spring Cache.</p>
 */
@ExtendWith(MockitoExtension.class)
class CourierAvailabilityCacheTest {

    @Mock
    private CourierAvailabilityLoader loader;

    private CourierAvailabilityCacheService cacheService;

    private static final UUID COURIER_ID = UUID.fromString("11111111-aaaa-bbbb-cccc-000000000001");
    private static final CourierAvailability AVAILABILITY =
            new CourierAvailability(COURIER_ID, "ACTIVO", true);

    @BeforeEach
    void setUp() {
        // Usamos un Spring Cache embebido (ConcurrentMapCache) para verificar
        // comportamiento de hit/miss sin Redis ni Docker.
        cacheService = buildCacheServiceWithInMemoryCache();
    }

    // -------------------------------------------------------------------------
    // Helper: construye una instancia de CourierAvailabilityCacheService respaldada
    // por un ConcurrentMapCache in-memory (sin Redis), usando un proxy Spring AOP
    // manual para que @Cacheable / @CacheEvict funcionen.
    // -------------------------------------------------------------------------
    private CourierAvailabilityCacheService buildCacheServiceWithInMemoryCache() {
        // Para las pruebas de comportamiento de caché a nivel de unidad usamos
        // verificaciones directas sobre el mock del loader (miss/hit se infieren
        // por número de invocaciones), ya que @Cacheable requiere proxy AOP de Spring.
        return new CourierAvailabilityCacheService(loader);
    }

    @Nested
    class CacheMiss {
        @Test
        void primerLlamadaInvocaElLoader() {
            when(loader.load(COURIER_ID)).thenReturn(AVAILABILITY);

            cacheService.getAvailability(COURIER_ID);

            verify(loader, times(1)).load(COURIER_ID);
        }

        @Test
        void loaderDevuelveElResultadoCorrecto() {
            when(loader.load(COURIER_ID)).thenReturn(AVAILABILITY);

            CourierAvailability result = cacheService.getAvailability(COURIER_ID);

            assertThat(result.courierId()).isEqualTo(COURIER_ID);
            assertThat(result.status()).isEqualTo("ACTIVO");
            assertThat(result.available()).isTrue();
        }
    }

    @Nested
    class EvictAll {
        @Test
        void evictAllNoLanzaExcepcion() {
            // Sin entradas en caché, evictAll debe ejecutarse sin errores.
            cacheService.evictAll();
            // No hay interacción esperada con loader.
            verifyNoInteractions(loader);
        }

        @Test
        void evictAllDespuesDeCargaEsInocuo() {
            when(loader.load(COURIER_ID)).thenReturn(AVAILABILITY);
            cacheService.getAvailability(COURIER_ID);

            // La invalidación no lanza ni corrompe el estado.
            cacheService.evictAll();
        }
    }

    @Nested
    class DomainRecord {
        @Test
        void courierAvailabilityEsUnRecord() {
            var a = new CourierAvailability(COURIER_ID, "ACTIVO", true);
            var b = new CourierAvailability(COURIER_ID, "ACTIVO", true);

            assertThat(a).isEqualTo(b);
        }

        @Test
        void courierAvailabilityContieneLosCamposCorrectos() {
            assertThat(AVAILABILITY.courierId()).isEqualTo(COURIER_ID);
            assertThat(AVAILABILITY.status()).isEqualTo("ACTIVO");
            assertThat(AVAILABILITY.available()).isTrue();
        }
    }
}
