package com.logitrack.shipment.service;

import com.logitrack.shipment.config.CacheConfig;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.support.ShipmentEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Pruebas de comportamiento para el bean {@link ShipmentQueryCache} usando proxies reales de Spring Cache.
 * Valida hits, misses, no colisión de claves lógicas y que las excepciones no se almacenen en caché.
 */
@SpringJUnitConfig(ShipmentQueryCacheTest.TestConfig.class)
class ShipmentQueryCacheTest {

    @Configuration
    @EnableCaching
    static class TestConfig {
        @Bean
        public ShipmentRepository shipmentRepository() {
            return mock(ShipmentRepository.class);
        }

        @Bean
        public ShipmentMapper shipmentMapper() {
            return new ShipmentMapper();
        }

        @Bean
        public ShipmentQueryCache shipmentQueryCache(ShipmentRepository repository, ShipmentMapper mapper) {
            return new ShipmentQueryCache(repository, mapper);
        }

        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.SHIPMENT_CACHE_NAME);
        }
    }

    @Autowired
    private ShipmentQueryCache queryCache;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        reset(shipments);
        var cache = cacheManager.getCache(CacheConfig.SHIPMENT_CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    @DisplayName("Tracking: la primera llamada consulta el repositorio y la segunda es un hit de caché")
    void trackingCodeFirstCallMissesAndSecondHits() {
        String trackingCode = "LT-TRK1234567";
        Shipment shipment = ShipmentEntities.shipment();
        ReflectionTestUtils.setField(shipment, "trackingCode", trackingCode);
        when(shipments.findByTrackingCode(trackingCode)).thenReturn(Optional.of(shipment));

        ShipmentResponse first = queryCache.findByTrackingCode(trackingCode);
        ShipmentResponse second = queryCache.findByTrackingCode(trackingCode);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.trackingCode()).isEqualTo(trackingCode);
        assertThat(second.trackingCode()).isEqualTo(trackingCode);
        verify(shipments, times(1)).findByTrackingCode(trackingCode);
    }

    @Test
    @DisplayName("ID: la primera llamada consulta el repositorio y la segunda es un hit de caché")
    void idFirstCallMissesAndSecondHits() {
        UUID id = UUID.randomUUID();
        Shipment shipment = ShipmentEntities.shipment();
        ReflectionTestUtils.setField(shipment, "id", id);
        when(shipments.findById(id)).thenReturn(Optional.of(shipment));

        ShipmentResponse first = queryCache.findById(id);
        ShipmentResponse second = queryCache.findById(id);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.id()).isEqualTo(id);
        assertThat(second.id()).isEqualTo(id);
        verify(shipments, times(1)).findById(id);
    }

    @Test
    @DisplayName("IDs y trackings diferentes no colisionan en la caché")
    void differentIdsAndTrackingCodesDoNotCollide() {
        UUID idA = UUID.randomUUID();
        UUID idB = UUID.randomUUID();
        String codeA = "LT-CODEAAAA01";
        String codeB = "LT-CODEBBBB02";

        Shipment shipmentA = ShipmentEntities.shipment();
        ReflectionTestUtils.setField(shipmentA, "id", idA);
        ReflectionTestUtils.setField(shipmentA, "trackingCode", codeA);

        Shipment shipmentB = ShipmentEntities.shipment();
        ReflectionTestUtils.setField(shipmentB, "id", idB);
        ReflectionTestUtils.setField(shipmentB, "trackingCode", codeB);

        when(shipments.findById(idA)).thenReturn(Optional.of(shipmentA));
        when(shipments.findById(idB)).thenReturn(Optional.of(shipmentB));
        when(shipments.findByTrackingCode(codeA)).thenReturn(Optional.of(shipmentA));
        when(shipments.findByTrackingCode(codeB)).thenReturn(Optional.of(shipmentB));

        ShipmentResponse resIdA = queryCache.findById(idA);
        ShipmentResponse resIdB = queryCache.findById(idB);
        ShipmentResponse resCodeA = queryCache.findByTrackingCode(codeA);
        ShipmentResponse resCodeB = queryCache.findByTrackingCode(codeB);

        assertThat(resIdA.id()).isEqualTo(idA);
        assertThat(resIdB.id()).isEqualTo(idB);
        assertThat(resCodeA.trackingCode()).isEqualTo(codeA);
        assertThat(resCodeB.trackingCode()).isEqualTo(codeB);

        // Repetimos para confirmar que cada clave atiende su propio hit sin colisiones
        queryCache.findById(idA);
        queryCache.findById(idB);
        queryCache.findByTrackingCode(codeA);
        queryCache.findByTrackingCode(codeB);

        verify(shipments, times(1)).findById(idA);
        verify(shipments, times(1)).findById(idB);
        verify(shipments, times(1)).findByTrackingCode(codeA);
        verify(shipments, times(1)).findByTrackingCode(codeB);
    }

    @Test
    @DisplayName("Un recurso inexistente lanza ResourceNotFoundException y no se almacena en caché")
    void nonExistingResourceThrowsAndIsNotCached() {
        UUID missingId = UUID.randomUUID();
        String missingCode = "LT-NONEXIST01";
        when(shipments.findById(missingId)).thenReturn(Optional.empty());
        when(shipments.findByTrackingCode(missingCode)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> queryCache.findById(missingId))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> queryCache.findById(missingId))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThatThrownBy(() -> queryCache.findByTrackingCode(missingCode))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> queryCache.findByTrackingCode(missingCode))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(shipments, times(2)).findById(missingId);
        verify(shipments, times(2)).findByTrackingCode(missingCode);
    }
}
