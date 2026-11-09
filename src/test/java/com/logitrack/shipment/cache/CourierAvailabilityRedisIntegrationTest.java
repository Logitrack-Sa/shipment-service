package com.logitrack.shipment.cache;

import com.logitrack.shipment.client.CourierFeignClient.CourierResult;
import com.logitrack.shipment.config.RedisCacheConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.logitrack.shipment.support.JwtTestSupport;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test de integración de la caché de repartidores con Redis real (Testcontainers).
 *
 * <p>Verifica:
 * <ol>
 *   <li>Primera consulta → cache MISS: escribe en Redis y llama al Feign client.</li>
 *   <li>Segunda consulta → cache HIT: sirve desde Redis sin llamar al Feign client.</li>
 *   <li>Invalidación total con {@code evictAll()} → siguiente consulta vuelve a MISS.</li>
 * </ol>
 * </p>
 *
 * <p><strong>Contenedores:</strong> Redis solamente (1 contenedor). MySQL no es necesario
 * porque se usa H2 (perfil {@code test}). Total: 1 contenedor (límite máximo: 3).</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
@Testcontainers
class CourierAvailabilityRedisIntegrationTest extends JwtTestSupport {

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine")
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private CourierAvailabilityCacheService cacheService;

    @Autowired
    private CacheManager cacheManager;

    /** Mock del Feign client para controlar las respuestas sin levantar courier-service. */
    @MockitoBean
    private com.logitrack.shipment.client.CourierFeignClient courierFeignClient;

    private static final UUID COURIER_ID = UUID.fromString("aaaaaaaa-1111-2222-3333-bbbbbbbbbbbb");

    @BeforeEach
    void setUp() {
        // Limpiar caché entre tests.
        var cache = cacheManager.getCache(RedisCacheConfig.COURIERS_AVAILABLE);
        if (cache != null) cache.clear();

        // Configurar el SecurityContext con un JWT de admin para que CourierAvailabilityLoader
        // no lance AccessDeniedException.
        Jwt jwt = Jwt.withTokenValue(token("admin"))
                .header("alg", "RS256")
                .subject("00000000-0000-0000-0000-000000000001")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(600))
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        var cache = cacheManager.getCache(RedisCacheConfig.COURIERS_AVAILABLE);
        if (cache != null) cache.clear();
    }

    @Test
    void primerConsultaEsMissYEscribeEnRedis() {
        CourierResult result = new CourierResult(COURIER_ID, "ACTIVO", true);
        when(courierFeignClient.getAvailability(eq(COURIER_ID), anyString())).thenReturn(result);

        CourierAvailability availability = cacheService.getAvailability(COURIER_ID);

        assertThat(availability.courierId()).isEqualTo(COURIER_ID);
        assertThat(availability.status()).isEqualTo("ACTIVO");
        assertThat(availability.available()).isTrue();
        verify(courierFeignClient, times(1)).getAvailability(eq(COURIER_ID), anyString());
    }

    @Test
    void segundaConsultaEsHitYNoLlamaAFeign() {
        CourierResult result = new CourierResult(COURIER_ID, "ACTIVO", true);
        when(courierFeignClient.getAvailability(eq(COURIER_ID), anyString())).thenReturn(result);

        // Primera llamada → MISS, escribe en caché.
        cacheService.getAvailability(COURIER_ID);
        // Segunda llamada → HIT, no toca Feign.
        CourierAvailability hitResult = cacheService.getAvailability(COURIER_ID);

        assertThat(hitResult.courierId()).isEqualTo(COURIER_ID);
        verify(courierFeignClient, times(1)).getAvailability(eq(COURIER_ID), anyString());
    }

    @Test
    void evictAllInvalidaLaCacheYLaSiguienteConsultaEsMiss() {
        CourierResult result = new CourierResult(COURIER_ID, "ACTIVO", true);
        when(courierFeignClient.getAvailability(eq(COURIER_ID), anyString())).thenReturn(result);

        // Primera llamada → MISS, escribe en caché.
        cacheService.getAvailability(COURIER_ID);
        verify(courierFeignClient, times(1)).getAvailability(eq(COURIER_ID), anyString());

        // Invalidación.
        cacheService.evictAll();

        // Siguiente llamada → MISS nuevamente, llama a Feign otra vez.
        cacheService.getAvailability(COURIER_ID);
        verify(courierFeignClient, times(2)).getAvailability(eq(COURIER_ID), anyString());
    }
}
