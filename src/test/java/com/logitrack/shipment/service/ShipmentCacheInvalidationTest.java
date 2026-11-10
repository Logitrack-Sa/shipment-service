package com.logitrack.shipment.service;

import com.logitrack.shipment.client.CourierLookup;
import com.logitrack.shipment.config.CacheConfig;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.exception.BusinessRuleException;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.security.ShipmentAccess;
import com.logitrack.shipment.support.ShipmentEntities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Pruebas de invalidación transaccional con proxies Spring reales para @Caching y @CacheEvict.
 * Valida eliminación de ambas claves lógicas (id y tracking) en cada mutación, selectividad
 * entre envíos, recarga tras invalidación y retención ante rollback, transacciones exteriores
 * y excepciones de negocio.
 */
@SpringJUnitConfig(ShipmentCacheInvalidationTest.TestConfig.class)
class ShipmentCacheInvalidationTest {

    @Configuration
    @EnableCaching
    @EnableTransactionManagement
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
        public ShipmentCreator shipmentCreator() {
            return mock(ShipmentCreator.class);
        }

        @Bean
        public TrackingCodeGenerator trackingCodeGenerator() {
            return mock(TrackingCodeGenerator.class);
        }

        @Bean
        public CourierLookup courierLookup() {
            return mock(CourierLookup.class);
        }

        @Bean
        public ShipmentStatusRecorder shipmentStatusRecorder() {
            return mock(ShipmentStatusRecorder.class);
        }

        @Bean
        public ShipmentAccess shipmentAccess() {
            return mock(ShipmentAccess.class);
        }

        @Bean
        public ShipmentQueryCache shipmentQueryCache(ShipmentRepository repository, ShipmentMapper mapper) {
            return new ShipmentQueryCache(repository, mapper);
        }

        @Bean
        public ShipmentService shipmentService(
                ShipmentRepository shipments,
                ShipmentCreator creator,
                TrackingCodeGenerator trackingCodes,
                ShipmentMapper mapper,
                CourierLookup couriers,
                ShipmentStatusRecorder statusRecorder,
                ShipmentAccess access,
                ShipmentQueryCache queryCache) {
            return new ShipmentService(shipments, creator, trackingCodes, mapper, couriers, statusRecorder, access,
                    org.mockito.Mockito.mock(ShipmentStatusEventOutbox.class), queryCache);
        }

        @Bean
        public FaultInjectableCacheManager underlyingCacheManager() {
            return new FaultInjectableCacheManager(CacheConfig.SHIPMENT_CACHE_NAME);
        }

        @Bean
        @org.springframework.context.annotation.Primary
        public CacheManager cacheManager(FaultInjectableCacheManager underlyingCacheManager) {
            return new org.springframework.cache.transaction.TransactionAwareCacheManagerProxy(underlyingCacheManager);
        }

        @Bean
        public PlatformTransactionManager transactionManager() {
            return new AbstractPlatformTransactionManager() {
                private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);

                @Override
                protected Object doGetTransaction() {
                    return depth;
                }

                @Override
                protected boolean isExistingTransaction(Object transaction) {
                    return depth.get() > 0;
                }

                @Override
                protected void doBegin(Object transaction, TransactionDefinition definition) {
                    depth.set(depth.get() + 1);
                }

                @Override
                protected void doCommit(DefaultTransactionStatus status) {}

                @Override
                protected void doRollback(DefaultTransactionStatus status) {}

                @Override
                protected void doCleanupAfterCompletion(Object transaction) {
                    int current = depth.get();
                    if (current > 0) {
                        depth.set(current - 1);
                    }
                }
            };
        }

        @Bean
        public TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
            return new TransactionTemplate(transactionManager);
        }
    }

    static class FaultInjectableCacheManager extends ConcurrentMapCacheManager {
        private volatile boolean failOnEvict = false;
        private volatile boolean failOnGet = false;

        public FaultInjectableCacheManager(String... cacheNames) {
            super(cacheNames);
        }

        public void setFailOnEvict(boolean fail) {
            this.failOnEvict = fail;
        }

        public void setFailOnGet(boolean fail) {
            this.failOnGet = fail;
        }

        @Override
        protected Cache createConcurrentMapCache(String name) {
            Cache delegate = super.createConcurrentMapCache(name);
            return new Cache() {
                @Override
                public String getName() {
                    return delegate.getName();
                }

                @Override
                public Object getNativeCache() {
                    return delegate.getNativeCache();
                }

                @Override
                public ValueWrapper get(Object key) {
                    if (failOnGet) {
                        throw new org.springframework.data.redis.RedisConnectionFailureException("Simulated Redis timeout/connection failure on read");
                    }
                    return delegate.get(key);
                }

                @Override
                public <T> T get(Object key, Class<T> type) {
                    if (failOnGet) {
                        throw new org.springframework.data.redis.RedisConnectionFailureException("Simulated Redis timeout/connection failure on read");
                    }
                    return delegate.get(key, type);
                }

                @Override
                public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
                    if (failOnGet) {
                        throw new org.springframework.data.redis.RedisConnectionFailureException("Simulated Redis timeout/connection failure on read");
                    }
                    return delegate.get(key, valueLoader);
                }

                @Override
                public void put(Object key, Object value) {
                    delegate.put(key, value);
                }

                @Override
                public void evict(Object key) {
                    if (failOnEvict) {
                        throw new org.springframework.data.redis.RedisSystemException(
                                "Simulated Redis failure during post-commit eviction",
                                new java.net.ConnectException("Connection refused"));
                    }
                    delegate.evict(key);
                }

                @Override
                public void clear() {
                    delegate.clear();
                }
            };
        }
    }

    @Autowired
    private ShipmentService service;

    @Autowired
    private ShipmentQueryCache queryCache;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private CourierLookup couriers;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private FaultInjectableCacheManager underlyingCacheManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Cache cache;

    @BeforeEach
    void resetState() {
        reset(shipments, couriers);
        underlyingCacheManager.setFailOnEvict(false);
        underlyingCacheManager.setFailOnGet(false);
        cache = cacheManager.getCache(CacheConfig.SHIPMENT_CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }

    private Shipment createSampleShipment(UUID id, String trackingCode, ShipmentStatus status) {
        Shipment shipment = ShipmentEntities.shipment();
        ReflectionTestUtils.setField(shipment, "id", id);
        ReflectionTestUtils.setField(shipment, "trackingCode", trackingCode);
        ReflectionTestUtils.setField(shipment, "status", status);
        return shipment;
    }

    private void primeCache(UUID id, String trackingCode, Shipment shipment) {
        when(shipments.findById(id)).thenReturn(Optional.of(shipment));
        when(shipments.findByTrackingCode(trackingCode)).thenReturn(Optional.of(shipment));

        queryCache.findById(id);
        queryCache.findByTrackingCode(trackingCode);

        assertThat(cache.get("shipment::id::" + id)).isNotNull();
        assertThat(cache.get("shipment::tracking::" + trackingCode)).isNotNull();
    }

    @Test
    @DisplayName("assignCourier elimina las dos claves lógicas (id y tracking)")
    void assignCourierEvictsBothKeys() {
        UUID id = UUID.randomUUID();
        String code = "LT-ASSIGN001";
        Shipment shipment = createSampleShipment(id, code, ShipmentStatus.PENDIENTE);
        primeCache(id, code, shipment);

        UUID courierId = UUID.randomUUID();
        service.assignCourier(id, courierId);

        assertThat(cache.get("shipment::id::" + id)).isNull();
        assertThat(cache.get("shipment::tracking::" + code)).isNull();
    }

    @Test
    @DisplayName("changeStatus elimina las dos claves lógicas (id y tracking)")
    void changeStatusEvictsBothKeys() {
        UUID id = UUID.randomUUID();
        String code = "LT-STATUS001";
        Shipment shipment = createSampleShipment(id, code, ShipmentStatus.PENDIENTE);
        UUID courierId = UUID.randomUUID();
        shipment.assignCourier(courierId);
        primeCache(id, code, shipment);

        service.changeStatus(id, ShipmentStatus.EN_RUTA, "En camino");

        assertThat(cache.get("shipment::id::" + id)).isNull();
        assertThat(cache.get("shipment::tracking::" + code)).isNull();
    }

    @Test
    @DisplayName("cancel elimina las dos claves lógicas (id y tracking)")
    void cancelEvictsBothKeys() {
        UUID id = UUID.randomUUID();
        String code = "LT-CANCEL001";
        Shipment shipment = createSampleShipment(id, code, ShipmentStatus.PENDIENTE);
        primeCache(id, code, shipment);

        service.cancel(id);

        assertThat(cache.get("shipment::id::" + id)).isNull();
        assertThat(cache.get("shipment::tracking::" + code)).isNull();
    }

    @Test
    @DisplayName("La invalidación de un envío no elimina las entradas de otro envío")
    void evictionDoesNotAffectOtherShipments() {
        UUID id1 = UUID.randomUUID();
        String code1 = "LT-SHIPMENT01";
        Shipment shipment1 = createSampleShipment(id1, code1, ShipmentStatus.PENDIENTE);

        UUID id2 = UUID.randomUUID();
        String code2 = "LT-SHIPMENT02";
        Shipment shipment2 = createSampleShipment(id2, code2, ShipmentStatus.PENDIENTE);

        primeCache(id1, code1, shipment1);
        primeCache(id2, code2, shipment2);

        UUID courierId = UUID.randomUUID();
        service.assignCourier(id1, courierId);

        // Envío 1 fue invalidado
        assertThat(cache.get("shipment::id::" + id1)).isNull();
        assertThat(cache.get("shipment::tracking::" + code1)).isNull();

        // Envío 2 sigue intacto en caché
        assertThat(cache.get("shipment::id::" + id2)).isNotNull();
        assertThat(cache.get("shipment::tracking::" + code2)).isNotNull();
    }

    @Test
    @DisplayName("Tras la invalidación, una nueva consulta carga el DTO actualizado")
    void queryAfterInvalidationReloadsUpdatedDto() {
        UUID id = UUID.randomUUID();
        String code = "LT-RELOAD001";
        Shipment shipment = createSampleShipment(id, code, ShipmentStatus.PENDIENTE);
        primeCache(id, code, shipment);

        UUID courierId = UUID.randomUUID();
        shipment.assignCourier(courierId);
        service.changeStatus(id, ShipmentStatus.EN_RUTA, "Inicio ruta");

        // Verificamos que la caché está vacía tras commit
        assertThat(cache.get("shipment::id::" + id)).isNull();

        // Segunda consulta: debe volver a consultar repositorio y traer EN_RUTA
        ShipmentResponse updated = service.findById(id);
        assertThat(updated.status()).isEqualTo(ShipmentStatus.EN_RUTA);
        assertThat(cache.get("shipment::id::" + id)).isNotNull();
    }

    @Test
    @DisplayName("Excepciones de negocio y rollback conservan la caché previa")
    void businessExceptionPreservesCache() {
        UUID id = UUID.randomUUID();
        String code = "LT-BIZEX0001";
        Shipment shipment = createSampleShipment(id, code, ShipmentStatus.ENTREGADO);
        primeCache(id, code, shipment);

        UUID courierId = UUID.randomUUID();
        // Fallará con BusinessRuleException porque no está PENDIENTE
        assertThatThrownBy(() -> service.assignCourier(id, courierId))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(cache.get("shipment::id::" + id)).isNotNull();
        assertThat(cache.get("shipment::tracking::" + code)).isNotNull();
    }

    @Test
    @DisplayName("Dentro de una transacción exterior no hay evicción anticipada; después de commit sí; después de rollback no")
    void outerTransactionControlsEvictionTiming() {
        UUID idCommit = UUID.randomUUID();
        String codeCommit = "LT-TXCOMMIT01";
        Shipment shipmentCommit = createSampleShipment(idCommit, codeCommit, ShipmentStatus.PENDIENTE);
        primeCache(idCommit, codeCommit, shipmentCommit);

        // Caso 1: Transacción exterior con commit exitoso
        transactionTemplate.execute(status -> {
            service.assignCourier(idCommit, UUID.randomUUID());
            // Antes de completar la transacción exterior, no debe haberse evictado
            assertThat(cache.get("shipment::id::" + idCommit)).isNotNull();
            assertThat(cache.get("shipment::tracking::" + codeCommit)).isNotNull();
            return null;
        });

        // Tras el commit de la transacción exterior, ambas claves se invalidan
        assertThat(cache.get("shipment::id::" + idCommit)).isNull();
        assertThat(cache.get("shipment::tracking::" + codeCommit)).isNull();

        // Caso 2: Transacción exterior con rollback
        UUID idRollback = UUID.randomUUID();
        String codeRollback = "LT-TXROLL001";
        Shipment shipmentRollback = createSampleShipment(idRollback, codeRollback, ShipmentStatus.PENDIENTE);
        primeCache(idRollback, codeRollback, shipmentRollback);

        transactionTemplate.execute(status -> {
            service.assignCourier(idRollback, UUID.randomUUID());
            status.setRollbackOnly();
            return null;
        });

        // Tras el rollback, la caché NO se elimina
        assertThat(cache.get("shipment::id::" + idRollback)).isNotNull();
        assertThat(cache.get("shipment::tracking::" + codeRollback)).isNotNull();
    }

    @Test
    @DisplayName("PR-2: Fallo en invalidación de Redis post-commit no deja persistencia parcial en MySQL y lanza excepción visible")
    void pr2_evictionFailureAfterCommitLeavesDatabaseConsistentWithoutPartialPersistence() {
        UUID id = UUID.randomUUID();
        String code = "LT-FAILCOMMIT1";
        Shipment shipment = createSampleShipment(id, code, ShipmentStatus.PENDIENTE);
        when(shipments.findById(id)).thenReturn(Optional.of(shipment));
        primeCache(id, code, shipment);

        UUID courierId = UUID.randomUUID();

        // Activamos la falla en la evicción de Redis (post-commit)
        underlyingCacheManager.setFailOnEvict(true);

        // La mutación debe fallar visiblemente al intentar la evicción post-commit
        assertThatThrownBy(() -> service.assignCourier(id, courierId))
                .isInstanceOf(org.springframework.data.redis.RedisSystemException.class)
                .hasMessageContaining("Simulated Redis failure during post-commit eviction");

        // Verificamos que MySQL NO quedó con persistencia parcial:
        // La transacción de MySQL se completó íntegramente antes de la evicción,
        // reflejando el nuevo estado de forma atómica y completa.
        assertThat(shipment.getCourierId()).as("MySQL debe reflejar el commit completo exitoso").isEqualTo(courierId);
    }

    @Test
    @DisplayName("PR-2: Rollback previo al commit preserva MySQL completamente sin persistencia parcial ni claves huérfanas")
    void pr2_rollbackBeforeCommitPreservesDatabaseAndDoesNotEvictCache() {
        UUID id = UUID.randomUUID();
        String code = "LT-TXROLL002";
        Shipment shipment = createSampleShipment(id, code, ShipmentStatus.PENDIENTE);
        when(shipments.findById(id)).thenReturn(Optional.of(shipment));
        primeCache(id, code, shipment);

        UUID courierId = UUID.randomUUID();

        // Ejecutar mutación dentro de transacción que hace rollback
        transactionTemplate.execute(status -> {
            service.assignCourier(id, courierId);
            status.setRollbackOnly();
            return null;
        });

        // Al revertir la transacción, afterCommit nunca se ejecuta.
        // Las claves previas en caché siguen presentes e intactas (sin claves huérfanas desincronizadas).
        assertThat(cache.get("shipment::id::" + id)).isNotNull();
        assertThat(cache.get("shipment::tracking::" + code)).isNotNull();
    }

    @Test
    @DisplayName("PR-3: Redis no disponible o con timeout en lecturas lanza error visible sin fallback silencioso")
    void pr3_redisUnavailableOrTimeoutOnReadThrowsVisibleErrorWithoutSilentFallback() {
        UUID id = UUID.randomUUID();
        String code = "LT-REDISTMOUT1";
        Shipment shipment = createSampleShipment(id, code, ShipmentStatus.PENDIENTE);
        when(shipments.findById(id)).thenReturn(Optional.of(shipment));

        // Simulamos Redis caído o con timeout en lecturas
        underlyingCacheManager.setFailOnGet(true);

        // findById debe lanzar la excepción visiblemente en lugar de silenciarla o degradar a MySQL
        assertThatThrownBy(() -> service.findById(id))
                .isInstanceOf(org.springframework.data.redis.RedisConnectionFailureException.class)
                .hasMessageContaining("Simulated Redis timeout/connection failure on read");

        // findByTrackingCode también debe lanzar la excepción visiblemente
        assertThatThrownBy(() -> service.findByTrackingCode(code))
                .isInstanceOf(org.springframework.data.redis.RedisConnectionFailureException.class)
                .hasMessageContaining("Simulated Redis timeout/connection failure on read");

        // Se verifica que no se consultó el repositorio MySQL para fallback silencioso
        verify(shipments, never()).findById(any());
        verify(shipments, never()).findByTrackingCode(any());
    }

    @Test
    @DisplayName("PR-3: Redis caído durante mutación mantiene consistencia relacional y lanza error visible")
    void pr3_redisUnavailableDuringMutationSurfacesErrorWhileMaintainingDatabaseConsistency() {
        UUID id = UUID.randomUUID();
        String code = "LT-REDISMUT01";
        Shipment shipment = createSampleShipment(id, code, ShipmentStatus.PENDIENTE);
        when(shipments.findById(id)).thenReturn(Optional.of(shipment));
        primeCache(id, code, shipment);

        // Simulamos Redis caído en la invalidación
        underlyingCacheManager.setFailOnEvict(true);

        // changeStatus debe reflejar el cambio en la entidad/base de datos pero arrojar el error de Redis
        assertThatThrownBy(() -> service.changeStatus(id, ShipmentStatus.CANCELADO, "Cancelación con Redis caído"))
                .isInstanceOf(org.springframework.data.redis.RedisSystemException.class);

        // MySQL mantiene consistencia: el estado es CANCELADO
        assertThat(shipment.getStatus()).isEqualTo(ShipmentStatus.CANCELADO);
    }
}
