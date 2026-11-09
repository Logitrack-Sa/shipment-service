package com.logitrack.shipment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/**
 * Configuración de prueba que provee una {@link RedisConnectionFactory} apuntando
 * a {@code localhost:6379} para todos los tests {@code @SpringBootTest} que NO usan
 * Testcontainers Redis.
 *
 * <p>En el test de integración Redis ({@code CourierAvailabilityRedisIntegrationTest})
 * el {@code @DynamicPropertySource} sobreescribe los valores de host y puerto, por lo
 * que esta configuración queda sin efecto (Lettuce usa los valores del
 * {@code application-test.yaml} inyectados dinámicamente).</p>
 *
 * <p>Para tests que no necesitan Redis real, se recomienda usar {@code @MockitoBean}
 * sobre {@code CourierAvailabilityCacheService} o excluir la auto-configuración en el
 * test específico.</p>
 */
@TestConfiguration
public class TestRedisConfig {

    /**
     * Fábrica de conexión Lettuce para tests. Usa los valores configurados en
     * {@code application-test.yaml} (o los dinámicos de Testcontainers si se usan).
     */
    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    public RedisConnectionFactory testRedisConnectionFactory() {
        return new LettuceConnectionFactory(new RedisStandaloneConfiguration("localhost", 6379));
    }
}
