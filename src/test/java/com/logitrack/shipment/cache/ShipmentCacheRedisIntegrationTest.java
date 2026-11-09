package com.logitrack.shipment.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.client.CourierLookup;
import com.logitrack.shipment.config.CacheConfig;
import com.logitrack.shipment.dto.AssignCourierRequest;
import com.logitrack.shipment.dto.ChangeStatusRequest;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.entity.Customer;
import com.logitrack.shipment.entity.Shipment;
import com.logitrack.shipment.entity.ShipmentStatus;
import com.logitrack.shipment.exception.BusinessRuleException;
import com.logitrack.shipment.exception.ResourceNotFoundException;
import com.logitrack.shipment.mapper.ShipmentMapper;
import com.logitrack.shipment.repository.AbstractMySqlRepositoryTest;
import com.logitrack.shipment.repository.CustomerRepository;
import com.logitrack.shipment.repository.ShipmentRepository;
import com.logitrack.shipment.repository.ShipmentStatusHistoryRepository;
import com.logitrack.shipment.service.ShipmentQueryCache;
import com.logitrack.shipment.service.ShipmentService;
import com.logitrack.shipment.support.JwtTestSupport;
import com.logitrack.shipment.support.ShipmentFixtures;
import jakarta.persistence.EntityManager;
import org.awaitility.Awaitility;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Suite de integración de extremo a extremo para el patrón Cache-Aside con Redis real y MySQL real.
 *
 * <p>Reutiliza el contenedor de MySQL compartido desde {@link AbstractMySqlRepositoryTest#MYSQL}
 * e inicia un contenedor dedicado de Redis (redis:7-alpine). Con Ryuk, el total simultáneo de
 * contenedores observados es exactamente 3, respetando el límite operativo estricto.</p>
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class ShipmentCacheRedisIntegrationTest extends JwtTestSupport {

    @Container
    protected static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        // Inicia el MySQL compartido preexistente y el Redis de pruebas.
        AbstractMySqlRepositoryTest.MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void configureContainers(DynamicPropertyRegistry registry) {
        var mysql = AbstractMySqlRepositoryTest.MYSQL;
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.datasource.driver-class-name", mysql::getDriverClassName);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", REDIS::getFirstMappedPort);
        registry.add("spring.cache.type", () -> "redis");
        registry.add("management.health.redis.enabled", () -> "true");
        registry.add("management.endpoint.health.show-details", () -> "always");
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private ShipmentService service;

    @Autowired
    private ShipmentQueryCache queryCache;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ShipmentRepository shipmentRepository;

    @Autowired
    private ShipmentStatusHistoryRepository historyRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private CourierLookup couriers;

    private Customer customerA;
    private Customer customerB;
    private Shipment shipmentA;

    private String idPhysicalKey(UUID id) {
        return "shipment_queries::shipment::id::" + id;
    }

    private String trackingPhysicalKey(String trackingCode) {
        return "shipment_queries::shipment::tracking::" + trackingCode;
    }

    private Statistics getHibernateStatistics() {
        return entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
    }

    private String bearer(String name) {
        return "Bearer " + token(name);
    }

    private String bearer(String name, Map<String, Object> claims) {
        return "Bearer " + token(name, claims);
    }

    private void setSecurityContext(String role, UUID customerId, UUID courierId) {
        var jwtBuilder = Jwt.withTokenValue("mock-" + role.toLowerCase() + "-token")
                .header("alg", "none")
                .subject(UUID.randomUUID().toString())
                .claim("roles", List.of(role.toUpperCase()));

        if (customerId != null) {
            jwtBuilder.claim("customerId", customerId.toString());
        }
        if (courierId != null) {
            jwtBuilder.claim("courierId", courierId.toString());
        }

        Jwt jwt = jwtBuilder.build();
        var auth = new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @BeforeEach
    void setUp() {
        var connection = redisTemplate.getConnectionFactory().getConnection();
        connection.serverCommands().flushAll();
        connection.close();

        historyRepository.deleteAll();
        shipmentRepository.deleteAll();
        customerRepository.deleteAll();

        customerA = customerRepository.saveAndFlush(new Customer("Cliente A", "clienteA@test.com", "123456789"));
        customerB = customerRepository.saveAndFlush(new Customer("Cliente B", "clienteB@test.com", "987654321"));

        var entityA = new ShipmentMapper().toEntity(ShipmentFixtures.request(), customerA, "LT-CACHE12345", new BigDecimal("2500.00"));
        shipmentA = shipmentRepository.saveAndFlush(entityA);

        doNothing().when(couriers).requireAvailable(any());
        doNothing().when(couriers).requireActive(any());

        setSecurityContext("ADMIN", null, null);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        historyRepository.deleteAll();
        shipmentRepository.deleteAll();
        customerRepository.deleteAll();
    }

    @Test
    @DisplayName("1. CacheManager: es RedisCacheManager con soporte transaccional y configuración de shipment_queries")
    void cacheManagerIsTransactionalRedisCacheManager() {
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
        RedisCacheManager redisCacheManager = (RedisCacheManager) cacheManager;
        assertThat(redisCacheManager.isTransactionAware())
                .as("RedisCacheManager debe ser transactionAware para sincronizar evicciones con el commit")
                .isTrue();

        org.springframework.cache.Cache cache = redisCacheManager.getCache(CacheConfig.SHIPMENT_CACHE_NAME);
        assertThat(cache).isNotNull();
    }

    @Test
    @DisplayName("2. Primera lectura (Miss): consulta MySQL, escribe claves físicas shipment_queries::... con JSON tipado")
    void firstReadMissQueriesMySqlAndPopulatesPhysicalKeysInRedis() throws Exception {
        String idKey = idPhysicalKey(shipmentA.getId());
        String trackingKey = trackingPhysicalKey(shipmentA.getTrackingCode());

        assertThat(redisTemplate.hasKey(idKey)).isFalse();
        assertThat(redisTemplate.hasKey(trackingKey)).isFalse();

        // Miss por ID
        ShipmentResponse byId = service.findById(shipmentA.getId());
        assertThat(byId).isNotNull();
        assertThat(byId.id()).isEqualTo(shipmentA.getId());
        assertThat(redisTemplate.hasKey(idKey)).isTrue();

        // Validar valor JSON tipado en Redis
        String idJson = redisTemplate.opsForValue().get(idKey);
        assertThat(idJson).isNotBlank();
        ShipmentResponse deserializedId = json.readValue(idJson, ShipmentResponse.class);
        assertThat(deserializedId.id()).isEqualTo(shipmentA.getId());
        assertThat(deserializedId.trackingCode()).isEqualTo(shipmentA.getTrackingCode());

        // Miss por Tracking
        ShipmentResponse byTracking = service.findByTrackingCode(shipmentA.getTrackingCode());
        assertThat(byTracking).isNotNull();
        assertThat(byTracking.trackingCode()).isEqualTo(shipmentA.getTrackingCode());
        assertThat(redisTemplate.hasKey(trackingKey)).isTrue();

        String trkJson = redisTemplate.opsForValue().get(trackingKey);
        assertThat(trkJson).isNotBlank();
        ShipmentResponse deserializedTrk = json.readValue(trkJson, ShipmentResponse.class);
        assertThat(deserializedTrk.trackingCode()).isEqualTo(shipmentA.getTrackingCode());
    }

    @Test
    @DisplayName("3. Segunda lectura (Hit): devuelve respuesta correcta sin sentencias SQL ni transacciones en DB (Hibernate statistics)")
    void secondReadHitReturnsDataWithoutSqlQueries() {
        // --- Control positivo del Miss (con claves ausentes) ---
        // Demuestra que con clave ausente, se ejecutan sentencias SQL y transacciones reales en MySQL,
        // asegurando que las estadísticas no den un falso positivo por inactividad.
        var missEntity = new ShipmentMapper().toEntity(ShipmentFixtures.request(), customerA, "LT-MISS00001", new BigDecimal("1500.00"));
        Shipment missShipment = shipmentRepository.saveAndFlush(missEntity);
        UUID missId = missShipment.getId();
        String missTrk = missShipment.getTrackingCode();

        Statistics statistics = getHibernateStatistics();
        statistics.clear();

        assertThat(redisTemplate.hasKey(idPhysicalKey(missId))).isFalse();
        assertThat(redisTemplate.hasKey(trackingPhysicalKey(missTrk))).isFalse();

        // Miss por ID: debe ejecutar SQL y abrir transacción en el loader
        ShipmentResponse missRespId = service.findById(missId);
        assertThat(missRespId).isNotNull();
        assertThat(statistics.getEntityLoadCount()).as("El miss debe cargar entidades desde MySQL").isGreaterThan(0);
        assertThat(statistics.getPrepareStatementCount()).as("El miss debe ejecutar sentencias SQL preparadas").isGreaterThan(0);
        assertThat(statistics.getTransactionCount()).as("El miss debe ejecutar y completar una transacción en MySQL").isGreaterThan(0);

        // Precargar tracking para missShipment fuera de las mediciones de Hit
        service.findByTrackingCode(missTrk);
        assertThat(redisTemplate.hasKey(idPhysicalKey(missId))).isTrue();
        assertThat(redisTemplate.hasKey(trackingPhysicalKey(missTrk))).isTrue();

        // --- Verificación de Hit por ID ---
        statistics.clear();
        ShipmentResponse hitId = service.findById(missId);
        assertThat(hitId.id()).isEqualTo(missId);
        assertThat(statistics.getEntityLoadCount())
                .as("Un hit por ID no debe cargar entidades de base de datos")
                .isEqualTo(0);
        assertThat(statistics.getPrepareStatementCount())
                .as("Un hit por ID no debe ejecutar sentencias SQL")
                .isEqualTo(0);
        assertThat(statistics.getTransactionCount())
                .as("Un hit por ID no debe abrir ni completar transacciones en MySQL")
                .isEqualTo(0);
        assertThat(statistics.getConnectCount())
                .as("Un hit por ID no debe solicitar conexiones JDBC a MySQL")
                .isEqualTo(0);

        // --- Verificación de Hit por Tracking ---
        statistics.clear();
        ShipmentResponse hitTrk = service.findByTrackingCode(missTrk);
        assertThat(hitTrk.trackingCode()).isEqualTo(missTrk);
        assertThat(statistics.getEntityLoadCount())
                .as("Un hit por tracking no debe cargar entidades de base de datos")
                .isEqualTo(0);
        assertThat(statistics.getPrepareStatementCount())
                .as("Un hit por tracking no debe ejecutar sentencias SQL")
                .isEqualTo(0);
        assertThat(statistics.getTransactionCount())
                .as("Un hit por tracking no debe abrir ni completar transacciones en MySQL")
                .isEqualTo(0);
        assertThat(statistics.getConnectCount())
                .as("Un hit por tracking no debe solicitar conexiones JDBC a MySQL")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("4. TTL: PTTL positivo cercano a 600000ms, no renovación en hit, expiración controlada y posterior miss")
    void ttlValidationWithToleranceNoRenewalOnHitAndFastExpiration() throws InterruptedException {
        String idKey = idPhysicalKey(shipmentA.getId());

        // Pre-cargar en caché
        service.findById(shipmentA.getId());

        // 4.1 Comprobar PTTL real cercano a 600000 ms (10 min)
        Long pttl = redisTemplate.getExpire(idKey, TimeUnit.MILLISECONDS);
        assertThat(pttl).isNotNull()
                .isGreaterThan(550_000L)
                .isLessThanOrEqualTo(600_000L);

        // 4.2 Comprobar que un hit no reinicia el TTL
        Thread.sleep(60);
        service.findById(shipmentA.getId()); // Hit
        Long pttlAfterHit = redisTemplate.getExpire(idKey, TimeUnit.MILLISECONDS);
        assertThat(pttlAfterHit).isNotNull()
                .isLessThanOrEqualTo(pttl);

        // 4.3 Comprobar expiración reduciendo únicamente el TTL de la clave de prueba
        redisTemplate.expire(idKey, Duration.ofMillis(200));
        Awaitility.await()
                .atMost(Duration.ofSeconds(3))
                .until(() -> !Boolean.TRUE.equals(redisTemplate.hasKey(idKey)));

        // Posterior lectura es un Miss que vuelve a consultar MySQL
        Statistics statistics = getHibernateStatistics();
        statistics.clear();
        ShipmentResponse reloaded = service.findById(shipmentA.getId());
        assertThat(reloaded).isNotNull();
        assertThat(statistics.getEntityLoadCount()).as("Tras la expiración, la consulta debe cargar desde MySQL").isGreaterThan(0);
        assertThat(redisTemplate.hasKey(idKey)).isTrue();
    }

    @Test
    @DisplayName("5. Invalidación: asignación, reasignación, cambio de estado y cancelación desalojan ambas claves tras commit")
    void reactiveInvalidationOnMutationsAndSelectivity() {
        String idKey = idPhysicalKey(shipmentA.getId());
        String trackingKey = trackingPhysicalKey(shipmentA.getTrackingCode());

        // --- 5.1 Asignación de repartidor ---
        service.findById(shipmentA.getId());
        service.findByTrackingCode(shipmentA.getTrackingCode());
        assertThat(redisTemplate.hasKey(idKey)).isTrue();
        assertThat(redisTemplate.hasKey(trackingKey)).isTrue();

        UUID courier1 = UUID.randomUUID();
        service.assignCourier(shipmentA.getId(), courier1);
        assertThat(redisTemplate.hasKey(idKey)).isFalse();
        assertThat(redisTemplate.hasKey(trackingKey)).isFalse();

        ShipmentResponse afterAssign = service.findById(shipmentA.getId());
        assertThat(afterAssign.courierId()).isEqualTo(courier1);

        // --- 5.2 Reasignación de repartidor ---
        service.findByTrackingCode(shipmentA.getTrackingCode());
        assertThat(redisTemplate.hasKey(idKey)).isTrue();
        assertThat(redisTemplate.hasKey(trackingKey)).isTrue();

        UUID courier2 = UUID.randomUUID();
        service.assignCourier(shipmentA.getId(), courier2);
        assertThat(redisTemplate.hasKey(idKey)).isFalse();
        assertThat(redisTemplate.hasKey(trackingKey)).isFalse();

        ShipmentResponse afterReassign = service.findById(shipmentA.getId());
        assertThat(afterReassign.courierId()).isEqualTo(courier2);

        // --- 5.3 Cambio de estado ---
        service.findByTrackingCode(shipmentA.getTrackingCode());
        assertThat(redisTemplate.hasKey(idKey)).isTrue();
        assertThat(redisTemplate.hasKey(trackingKey)).isTrue();

        service.changeStatus(shipmentA.getId(), ShipmentStatus.EN_RUTA, "Inicio de ruta");
        assertThat(redisTemplate.hasKey(idKey)).isFalse();
        assertThat(redisTemplate.hasKey(trackingKey)).isFalse();

        ShipmentResponse afterStatus = service.findById(shipmentA.getId());
        assertThat(afterStatus.status()).isEqualTo(ShipmentStatus.EN_RUTA);

        // --- 5.4 Cancelación en otro envío PENDIENTE ---
        var entityB = new ShipmentMapper().toEntity(ShipmentFixtures.request(), customerA, "LT-CANCEL001", new BigDecimal("1800.00"));
        Shipment shipmentCancel = shipmentRepository.saveAndFlush(entityB);
        String cancelIdKey = idPhysicalKey(shipmentCancel.getId());
        String cancelTrkKey = trackingPhysicalKey(shipmentCancel.getTrackingCode());

        service.findById(shipmentCancel.getId());
        service.findByTrackingCode(shipmentCancel.getTrackingCode());
        assertThat(redisTemplate.hasKey(cancelIdKey)).isTrue();
        assertThat(redisTemplate.hasKey(cancelTrkKey)).isTrue();

        service.cancel(shipmentCancel.getId());
        assertThat(redisTemplate.hasKey(cancelIdKey)).isFalse();
        assertThat(redisTemplate.hasKey(cancelTrkKey)).isFalse();

        ShipmentResponse afterCancel = service.findById(shipmentCancel.getId());
        assertThat(afterCancel.status()).isEqualTo(ShipmentStatus.CANCELADO);

        // --- 5.5 Selectividad: mutar un envío no afecta las entradas de otro ---
        var entityC = new ShipmentMapper().toEntity(ShipmentFixtures.request(), customerA, "LT-SELECT01", new BigDecimal("1200.00"));
        Shipment shipmentC = shipmentRepository.saveAndFlush(entityC);
        String cIdKey = idPhysicalKey(shipmentC.getId());
        String cTrkKey = trackingPhysicalKey(shipmentC.getTrackingCode());

        service.findById(shipmentA.getId());
        service.findById(shipmentC.getId());
        service.findByTrackingCode(shipmentC.getTrackingCode());

        assertThat(redisTemplate.hasKey(cIdKey)).isTrue();
        assertThat(redisTemplate.hasKey(cTrkKey)).isTrue();

        // Mutar shipmentCancel no debe desalojar shipmentC
        service.changeStatus(shipmentA.getId(), ShipmentStatus.ENTREGADO, "Entrega final");

        assertThat(redisTemplate.hasKey(cIdKey)).isTrue();
        assertThat(redisTemplate.hasKey(cTrkKey)).isTrue();
    }

    @Test
    @DisplayName("6. Transacciones: Claves persisten durante tx, se invalidan tras commit, permanecen tras rollback y ante excepción de negocio")
    void transactionSynchronizationCommitRollbackAndBusinessException() {
        var txTemplate = new TransactionTemplate(transactionManager);
        String idKey = idPhysicalKey(shipmentA.getId());
        String trkKey = trackingPhysicalKey(shipmentA.getTrackingCode());

        // Precargar en caché
        service.findById(shipmentA.getId());
        service.findByTrackingCode(shipmentA.getTrackingCode());
        assertThat(redisTemplate.hasKey(idKey)).isTrue();
        assertThat(redisTemplate.hasKey(trkKey)).isTrue();

        UUID courier1 = UUID.randomUUID();

        // 6.1 Commit exterior: dentro de la transacción siguen presentes, tras commit se eliminan
        txTemplate.execute(status -> {
            service.assignCourier(shipmentA.getId(), courier1);
            assertThat(redisTemplate.hasKey(idKey)).isTrue();
            assertThat(redisTemplate.hasKey(trkKey)).isTrue();
            return null;
        });

        assertThat(redisTemplate.hasKey(idKey)).isFalse();
        assertThat(redisTemplate.hasKey(trkKey)).isFalse();

        // 6.2 Rollback exterior: tras rollback las claves permanecen y MySQL conserva estado anterior
        service.findById(shipmentA.getId());
        service.findByTrackingCode(shipmentA.getTrackingCode());
        assertThat(redisTemplate.hasKey(idKey)).isTrue();
        assertThat(redisTemplate.hasKey(trkKey)).isTrue();

        UUID rollbackCourier = UUID.randomUUID();
        txTemplate.execute(status -> {
            service.assignCourier(shipmentA.getId(), rollbackCourier);
            status.setRollbackOnly();
            return null;
        });

        // Tras rollback no se desalojan de Redis
        assertThat(redisTemplate.hasKey(idKey)).isTrue();
        assertThat(redisTemplate.hasKey(trkKey)).isTrue();
        Shipment fromDb = shipmentRepository.findById(shipmentA.getId()).orElseThrow();
        assertThat(fromDb.getCourierId()).isEqualTo(courier1);

        // 6.3 Excepción de negocio: no modifica MySQL ni invalida claves
        service.changeStatus(shipmentA.getId(), ShipmentStatus.EN_RUTA, "En ruta");
        // Precargar en EN_RUTA
        service.findById(shipmentA.getId());
        service.findByTrackingCode(shipmentA.getTrackingCode());
        assertThat(redisTemplate.hasKey(idKey)).isTrue();
        assertThat(redisTemplate.hasKey(trkKey)).isTrue();

        UUID invalidCourier = UUID.randomUUID();
        // Intentar asignar courier a un envío EN_RUTA lanza BusinessRuleException
        assertThatThrownBy(() -> service.assignCourier(shipmentA.getId(), invalidCourier))
                .isInstanceOf(BusinessRuleException.class);

        assertThat(redisTemplate.hasKey(idKey)).isTrue();
        assertThat(redisTemplate.hasKey(trkKey)).isTrue();
        Shipment unchangedDb = shipmentRepository.findById(shipmentA.getId()).orElseThrow();
        assertThat(unchangedDb.getCourierId()).isEqualTo(courier1);
    }

    @Test
    @DisplayName("7. Seguridad: Autorización ejecutada en hits (MockMvc y fachada), denegación a cliente/repartidor ajeno y actualización en reasignación")
    void securityEnforcedOnCacheHitAndCourierReassignment() throws Exception {
        UUID shipmentId = shipmentA.getId();
        String trackingCode = shipmentA.getTrackingCode();
        UUID ownerId = customerA.getId();
        UUID otherCustomerId = customerB.getId();
        UUID courierAssigned = UUID.randomUUID();
        UUID courierOther = UUID.randomUUID();

        // 7.1 ADMIN precarga ambas rutas
        mvc.perform(get("/api/v1/shipments/" + shipmentId)
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/shipments/tracking/" + trackingCode)
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isOk());

        assertThat(redisTemplate.hasKey(idPhysicalKey(shipmentId))).isTrue();
        assertThat(redisTemplate.hasKey(trackingPhysicalKey(trackingCode))).isTrue();

        // 7.2 Cliente ajeno recibe 403 Forbidden en ambas rutas en pleno Cache Hit
        mvc.perform(get("/api/v1/shipments/" + shipmentId)
                        .header("Authorization", bearer("cliente-b", Map.of("customerId", otherCustomerId.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mvc.perform(get("/api/v1/shipments/tracking/" + trackingCode)
                        .header("Authorization", bearer("cliente-b", Map.of("customerId", otherCustomerId.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // Validar también a nivel de fachada de servicio
        setSecurityContext("CLIENTE", otherCustomerId, null);
        assertThatThrownBy(() -> service.findById(shipmentId)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.findByTrackingCode(trackingCode)).isInstanceOf(AccessDeniedException.class);

        // 7.3 Propietario puede consultar por ambas rutas
        mvc.perform(get("/api/v1/shipments/" + shipmentId)
                        .header("Authorization", bearer("cliente-a", Map.of("customerId", ownerId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shipmentId.toString()));

        mvc.perform(get("/api/v1/shipments/tracking/" + trackingCode)
                        .header("Authorization", bearer("cliente-a", Map.of("customerId", ownerId.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingCode").value(trackingCode));

        // 7.4 Asignar repartidor y validar acceso de repartidores
        setSecurityContext("ADMIN", null, null);
        service.assignCourier(shipmentId, courierAssigned);
        // Pre-cargar en caché tras asignación
        service.findById(shipmentId);
        service.findByTrackingCode(trackingCode);

        // Repartidor asignado puede leer
        mvc.perform(get("/api/v1/shipments/" + shipmentId)
                        .header("Authorization", bearer("repartidor-a", Map.of("courierId", courierAssigned.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(courierAssigned.toString()));

        // Repartidor ajeno recibe 403
        mvc.perform(get("/api/v1/shipments/" + shipmentId)
                        .header("Authorization", bearer("repartidor-b", Map.of("courierId", courierOther.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // 7.5 Tras reasignación confirmada a courierOther, el anterior pierde acceso y el nuevo accede
        setSecurityContext("ADMIN", null, null);
        service.assignCourier(shipmentId, courierOther);
        // Pre-cargar en caché
        service.findById(shipmentId);
        service.findByTrackingCode(trackingCode);

        // Verificar que ambas claves físicas están presentes en Redis (el escenario ejercita hits de caché)
        assertThat(redisTemplate.hasKey(idPhysicalKey(shipmentId))).isTrue();
        assertThat(redisTemplate.hasKey(trackingPhysicalKey(trackingCode))).isTrue();

        // Anterior repartidor recibe 403 por ID
        mvc.perform(get("/api/v1/shipments/" + shipmentId)
                        .header("Authorization", bearer("repartidor-a", Map.of("courierId", courierAssigned.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // Anterior repartidor recibe 403 por Tracking (ejercitando un hit en caché)
        mvc.perform(get("/api/v1/shipments/tracking/" + trackingCode)
                        .header("Authorization", bearer("repartidor-a", Map.of("courierId", courierAssigned.toString()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        // Nuevo repartidor accede por ambas rutas
        mvc.perform(get("/api/v1/shipments/" + shipmentId)
                        .header("Authorization", bearer("repartidor-b", Map.of("courierId", courierOther.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(courierOther.toString()));

        mvc.perform(get("/api/v1/shipments/tracking/" + trackingCode)
                        .header("Authorization", bearer("repartidor-b", Map.of("courierId", courierOther.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courierId").value(courierOther.toString()));
    }

    @Test
    @DisplayName("8. Ausencias: ID y tracking inexistentes devuelven 404 y no dejan entradas negativas en Redis")
    void nonExistentResourceReturns404AndLeavesNoNegativeCache() throws Exception {
        UUID missingId = UUID.randomUUID();
        String missingTrk = "LT-NOTFOUND99";

        mvc.perform(get("/api/v1/shipments/" + missingId)
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mvc.perform(get("/api/v1/shipments/tracking/" + missingTrk)
                        .header("Authorization", bearer("admin")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(redisTemplate.hasKey(idPhysicalKey(missingId))).isFalse();
        assertThat(redisTemplate.hasKey(trackingPhysicalKey(missingTrk))).isFalse();
    }

    @Test
    @DisplayName("9. Actuator Health: con Redis levantado en el contenedor, /actuator/health responde UP")
    void actuatorHealthWithRealRedisReportsUp() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.redis.status").value("UP"));
    }

    @Test
    @DisplayName("10. Cancelación por changeStatus: desaloja ambas claves tras commit, actualiza MySQL y repuebla como CANCELADO")
    void cancellationViaChangeStatusEvictsBothKeysAndUpdatesMySql() {
        var entity = new ShipmentMapper().toEntity(ShipmentFixtures.request(), customerA, "LT-STATCAN01", new BigDecimal("3200.00"));
        Shipment shipmentStatusCancel = shipmentRepository.saveAndFlush(entity);
        UUID shipmentId = shipmentStatusCancel.getId();
        String trackingCode = shipmentStatusCancel.getTrackingCode();
        String idKey = idPhysicalKey(shipmentId);
        String trkKey = trackingPhysicalKey(trackingCode);

        // Precargar por ID y tracking y verificar ambas claves físicas existentes
        service.findById(shipmentId);
        service.findByTrackingCode(trackingCode);
        assertThat(redisTemplate.hasKey(idKey)).isTrue();
        assertThat(redisTemplate.hasKey(trkKey)).isTrue();

        // Ejecutar service.changeStatus(id, ShipmentStatus.CANCELADO, observacion) como usuario autorizado
        setSecurityContext("ADMIN", null, null);
        service.changeStatus(shipmentId, ShipmentStatus.CANCELADO, "Cancelación vía changeStatus");

        // Después del commit comprobar que ambas claves desaparecieron
        assertThat(redisTemplate.hasKey(idKey)).isFalse();
        assertThat(redisTemplate.hasKey(trkKey)).isFalse();

        // Consultar directamente MySQL para comprobar CANCELADO
        Shipment fromDb = shipmentRepository.findById(shipmentId).orElseThrow();
        assertThat(fromDb.getStatus()).isEqualTo(ShipmentStatus.CANCELADO);

        // Consultar nuevamente por ambas rutas y comprobar CANCELADO y repoblación de ambas claves
        ShipmentResponse reloadedById = service.findById(shipmentId);
        assertThat(reloadedById.status()).isEqualTo(ShipmentStatus.CANCELADO);
        assertThat(redisTemplate.hasKey(idKey)).isTrue();

        ShipmentResponse reloadedByTrk = service.findByTrackingCode(trackingCode);
        assertThat(reloadedByTrk.status()).isEqualTo(ShipmentStatus.CANCELADO);
        assertThat(redisTemplate.hasKey(trkKey)).isTrue();
    }
}
