package com.logitrack.shipment.security;

import com.logitrack.shipment.client.CourierLookup;
import com.logitrack.shipment.config.CacheConfig;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.service.ShipmentCreator;
import com.logitrack.shipment.service.ShipmentQueryCache;
import com.logitrack.shipment.service.ShipmentService;
import com.logitrack.shipment.service.ShipmentStatusRecorder;
import com.logitrack.shipment.service.TrackingCodeGenerator;
import com.logitrack.shipment.support.ShipmentEntities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Pruebas de seguridad e hit de caché con el bean real {@link ShipmentAccess} y {@link SecurityContextHolder}.
 * Verifica que la fachada ejecute autorización sobre los datos cacheados sin consultar el repositorio,
 * y que las reasignaciones revoquen el acceso de repartidores anteriores.
 */
@SpringJUnitConfig(ShipmentCacheSecurityTest.TestConfig.class)
class ShipmentCacheSecurityTest {

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
        public ShipmentAccess shipmentAccess() {
            return new ShipmentAccess();
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
            return new ShipmentService(shipments, creator, trackingCodes, mapper, couriers, statusRecorder, access, queryCache);
        }

        @Bean
        public CacheManager cacheManager() {
            return new org.springframework.cache.transaction.TransactionAwareCacheManagerProxy(
                    new ConcurrentMapCacheManager(CacheConfig.SHIPMENT_CACHE_NAME));
        }

        static class TrackingTransactionManager extends AbstractPlatformTransactionManager {
            private final ThreadLocal<Integer> depth = ThreadLocal.withInitial(() -> 0);
            private final java.util.concurrent.atomic.AtomicInteger transactionBegins = new java.util.concurrent.atomic.AtomicInteger(0);

            public int getTransactionBegins() {
                return transactionBegins.get();
            }

            public void resetTransactionBegins() {
                transactionBegins.set(0);
            }

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
                transactionBegins.incrementAndGet();
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
        }

        @Bean
        public TrackingTransactionManager transactionManager() {
            return new TrackingTransactionManager();
        }
    }

    @Autowired
    private ShipmentService service;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private TestConfig.TrackingTransactionManager transactionManager;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setup() {
        SecurityContextHolder.clearContext();
        reset(shipments);
        var cache = cacheManager.getCache(CacheConfig.SHIPMENT_CACHE_NAME);
        if (cache != null) {
            cache.clear();
        }
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateAdmin() {
        Jwt jwt = Jwt.withTokenValue("mock-admin-token")
                .header("alg", "none")
                .claim("sub", "admin-user")
                .build();
        var auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void authenticateCustomer(UUID customerId) {
        Jwt jwt = Jwt.withTokenValue("mock-client-token")
                .header("alg", "none")
                .claim("customerId", customerId != null ? customerId.toString() : "")
                .build();
        var auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_CLIENTE")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private void authenticateCourier(UUID courierId) {
        Jwt jwt = Jwt.withTokenValue("mock-courier-token")
                .header("alg", "none")
                .claim("courierId", courierId != null ? courierId.toString() : "")
                .build();
        var auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_REPARTIDOR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Shipment createShipment(UUID id, String trackingCode, UUID customerId, UUID courierId) {
        Shipment shipment = ShipmentEntities.shipment();
        ReflectionTestUtils.setField(shipment, "id", id);
        ReflectionTestUtils.setField(shipment, "trackingCode", trackingCode);
        ReflectionTestUtils.setField(shipment.getCustomer(), "id", customerId);
        ReflectionTestUtils.setField(shipment, "status", ShipmentStatus.PENDIENTE);
        if (courierId != null) {
            shipment.assignCourier(courierId);
        }
        return shipment;
    }

    @Test
    @DisplayName("ADMIN precarga la caché y un cliente ajeno sigue recibiendo AccessDenied sin tocar el repositorio")
    void adminPreloadsAndOtherClientReceivesAccessDeniedOnCacheHit() {
        UUID shipmentId = UUID.randomUUID();
        String trackingCode = "LT-SEC0001";
        UUID ownerCustomerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();

        Shipment shipment = createShipment(shipmentId, trackingCode, ownerCustomerId, null);
        when(shipments.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(shipments.findByTrackingCode(trackingCode)).thenReturn(Optional.of(shipment));

        // ADMIN precarga ambas claves
        authenticateAdmin();
        ShipmentResponse adminResId = service.findById(shipmentId);
        ShipmentResponse adminResTrk = service.findByTrackingCode(trackingCode);
        assertThat(adminResId).isNotNull();
        assertThat(adminResTrk).isNotNull();
        verify(shipments, times(1)).findById(shipmentId);
        verify(shipments, times(1)).findByTrackingCode(trackingCode);

        // Cliente no propietario intenta acceder: hit en caché, pero denegado por autorización en fachada
        authenticateCustomer(otherCustomerId);
        assertThatThrownBy(() -> service.findById(shipmentId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.findByTrackingCode(trackingCode))
                .isInstanceOf(AccessDeniedException.class);

        // Repositorio no fue consultado para la verificación de acceso
        verify(shipments, times(1)).findById(shipmentId);
        verify(shipments, times(1)).findByTrackingCode(trackingCode);
    }

    @Test
    @DisplayName("Cliente propietario puede leer el envío por ID y por código de seguimiento")
    void ownerCustomerCanRead() {
        UUID shipmentId = UUID.randomUUID();
        String trackingCode = "LT-SEC0002";
        UUID ownerCustomerId = UUID.randomUUID();

        Shipment shipment = createShipment(shipmentId, trackingCode, ownerCustomerId, null);
        when(shipments.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(shipments.findByTrackingCode(trackingCode)).thenReturn(Optional.of(shipment));

        authenticateCustomer(ownerCustomerId);
        ShipmentResponse byId = service.findById(shipmentId);
        ShipmentResponse byTrk = service.findByTrackingCode(trackingCode);

        assertThat(byId.id()).isEqualTo(shipmentId);
        assertThat(byTrk.trackingCode()).isEqualTo(trackingCode);
    }

    @Test
    @DisplayName("Repartidor asignado puede leer y repartidor ajeno no puede leer")
    void courierAccessControl() {
        UUID shipmentId = UUID.randomUUID();
        String trackingCode = "LT-SEC0003";
        UUID ownerCustomerId = UUID.randomUUID();
        UUID assignedCourier = UUID.randomUUID();
        UUID otherCourier = UUID.randomUUID();

        Shipment shipment = createShipment(shipmentId, trackingCode, ownerCustomerId, assignedCourier);
        when(shipments.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(shipments.findByTrackingCode(trackingCode)).thenReturn(Optional.of(shipment));

        // Repartidor asignado puede leer
        authenticateCourier(assignedCourier);
        assertThat(service.findById(shipmentId).courierId()).isEqualTo(assignedCourier);
        assertThat(service.findByTrackingCode(trackingCode).courierId()).isEqualTo(assignedCourier);

        // Repartidor ajeno recibe AccessDenied
        authenticateCourier(otherCourier);
        assertThatThrownBy(() -> service.findById(shipmentId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.findByTrackingCode(trackingCode))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Tras reasignación y commit, el repartidor anterior pierde acceso y el nuevo puede leer por ambas rutas")
    void reassignmentRevokesPreviousCourierAndGrantsNewCourier() {
        UUID shipmentId = UUID.randomUUID();
        String trackingCode = "LT-SEC0004";
        UUID ownerCustomerId = UUID.randomUUID();
        UUID oldCourier = UUID.randomUUID();
        UUID newCourier = UUID.randomUUID();

        Shipment shipment = createShipment(shipmentId, trackingCode, ownerCustomerId, oldCourier);
        when(shipments.findById(shipmentId)).thenReturn(Optional.of(shipment));
        when(shipments.findByTrackingCode(trackingCode)).thenReturn(Optional.of(shipment));

        // Repartidor antiguo lee y puebla caché
        authenticateCourier(oldCourier);
        assertThat(service.findById(shipmentId).courierId()).isEqualTo(oldCourier);
        assertThat(service.findByTrackingCode(trackingCode).courierId()).isEqualTo(oldCourier);

        // ADMIN reasigna a nuevo repartidor
        authenticateAdmin();
        service.assignCourier(shipmentId, newCourier);

        // Repartidor anterior intenta leer: denegado por ID y tracking
        authenticateCourier(oldCourier);
        assertThatThrownBy(() -> service.findById(shipmentId))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.findByTrackingCode(trackingCode))
                .isInstanceOf(AccessDeniedException.class);

        // Repartidor nuevo puede leer por ambas rutas
        authenticateCourier(newCourier);
        assertThat(service.findById(shipmentId).courierId()).isEqualTo(newCourier);
        assertThat(service.findByTrackingCode(trackingCode).courierId()).isEqualTo(newCourier);
    }

    @Test
    @DisplayName("Un hit por ID y tracking no inicia transacción desde la fachada y ejecuta autorización; un miss ejecuta el repositorio dentro de la transacción del loader")
    void cacheHitDoesNotStartTransactionFromFacadeAndExecutesAuthorizationWhileMissRunsInLoaderTransaction() {
        UUID shipmentId = UUID.randomUUID();
        String trackingCode = "LT-NOTX0001";
        UUID ownerCustomerId = UUID.randomUUID();
        UUID otherCustomerId = UUID.randomUUID();

        Shipment shipment = createShipment(shipmentId, trackingCode, ownerCustomerId, null);

        // Validamos que al consultar el repositorio en el MISS, una transacción esté activa
        when(shipments.findById(shipmentId)).thenAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                    .as("La consulta del repositorio en un miss debe ejecutarse dentro de la transacción del loader")
                    .isTrue();
            return Optional.of(shipment);
        });
        when(shipments.findByTrackingCode(trackingCode)).thenAnswer(invocation -> {
            assertThat(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
                    .as("La consulta del repositorio en un miss debe ejecutarse dentro de la transacción del loader")
                    .isTrue();
            return Optional.of(shipment);
        });

        authenticateCustomer(ownerCustomerId);
        transactionManager.resetTransactionBegins();

        // --- 1. MISS POR ID ---
        int txBeforeMissId = transactionManager.getTransactionBegins();
        ShipmentResponse resMissId = service.findById(shipmentId);
        assertThat(resMissId).isNotNull();
        // El loader inició 1 transacción readOnly para consultar el repositorio
        assertThat(transactionManager.getTransactionBegins()).isEqualTo(txBeforeMissId + 1);
        verify(shipments, times(1)).findById(shipmentId);

        // --- 2. HIT POR ID CON PROPIETARIO ---
        int txBeforeHitId = transactionManager.getTransactionBegins();
        ShipmentResponse resHitId = service.findById(shipmentId);
        assertThat(resHitId).isNotNull();
        // El hit NO inicia transacción (la fachada no tiene @Transactional)
        assertThat(transactionManager.getTransactionBegins())
                .as("Un hit por ID no debe iniciar ninguna transacción desde la fachada")
                .isEqualTo(txBeforeHitId);
        verify(shipments, times(1)).findById(shipmentId);

        // --- 3. HIT POR ID CON CLIENTE AJENO (EJECUTA AUTORIZACIÓN EN HIT) ---
        authenticateCustomer(otherCustomerId);
        assertThatThrownBy(() -> service.findById(shipmentId))
                .isInstanceOf(AccessDeniedException.class);
        // Sigue sin iniciar transacción y sin tocar repositorio
        assertThat(transactionManager.getTransactionBegins()).isEqualTo(txBeforeHitId);
        verify(shipments, times(1)).findById(shipmentId);

        // --- 4. MISS POR TRACKING ---
        authenticateCustomer(ownerCustomerId);
        int txBeforeMissTrk = transactionManager.getTransactionBegins();
        ShipmentResponse resMissTrk = service.findByTrackingCode(trackingCode);
        assertThat(resMissTrk).isNotNull();
        assertThat(transactionManager.getTransactionBegins()).isEqualTo(txBeforeMissTrk + 1);
        verify(shipments, times(1)).findByTrackingCode(trackingCode);

        // --- 5. HIT POR TRACKING CON PROPIETARIO ---
        int txBeforeHitTrk = transactionManager.getTransactionBegins();
        ShipmentResponse resHitTrk = service.findByTrackingCode(trackingCode);
        assertThat(resHitTrk).isNotNull();
        // El hit por tracking NO inicia transacción
        assertThat(transactionManager.getTransactionBegins())
                .as("Un hit por tracking no debe iniciar ninguna transacción desde la fachada")
                .isEqualTo(txBeforeHitTrk);
        verify(shipments, times(1)).findByTrackingCode(trackingCode);

        // --- 6. HIT POR TRACKING CON CLIENTE AJENO ---
        authenticateCustomer(otherCustomerId);
        assertThatThrownBy(() -> service.findByTrackingCode(trackingCode))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(transactionManager.getTransactionBegins()).isEqualTo(txBeforeHitTrk);
        verify(shipments, times(1)).findByTrackingCode(trackingCode);
    }
}
