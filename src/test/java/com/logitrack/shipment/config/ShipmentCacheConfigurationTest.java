package com.logitrack.shipment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.dto.AddressDto;
import com.logitrack.shipment.dto.ShipmentResponse;
import com.logitrack.shipment.entity.ShipmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.cache.CacheAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pruebas unitarias de configuración de caché y serialización Jackson para Redis.
 *
 * <p>Nota metodológica: La expiración real basada en TTL de Redis (10 minutos) no puede
 * demostrarse mediante cachés en memoria ni es admisible usar Thread.sleep para esperar
 * dicho lapso en la suite de pruebas. Por tanto, se valida inspeccionando las propiedades
 * estáticas del bean de configuración de producción (RedisCacheConfiguration).</p>
 */
class ShipmentCacheConfigurationTest {

    @Test
    @DisplayName("Serialización JSON de ida y vuelta de ShipmentResponse completo con courierId presente y nulo")
    void jsonSerializationRoundTripReturnsTypedShipmentResponseNotMap() {
        ObjectMapper objectMapper = CacheConfig.createCacheObjectMapper();
        Jackson2JsonRedisSerializer<ShipmentResponse> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, ShipmentResponse.class);

        // Caso con todos los campos poblados (incluyendo courierId)
        ShipmentResponse fullResponse = new ShipmentResponse(
                UUID.randomUUID(),
                "LT-FULLROUND1",
                UUID.randomUUID(),
                new AddressDto("Av. San Martín", "456", "San Salvador de Jujuy", "4600"),
                new AddressDto("Belgrano", "789", "Salta", "4400"),
                new BigDecimal("12.50"),
                new BigDecimal("40.00"),
                new BigDecimal("30.00"),
                new BigDecimal("20.00"),
                new BigDecimal("4500.75"),
                ShipmentStatus.EN_RUTA,
                UUID.randomUUID(),
                Instant.parse("2026-11-08T18:00:00Z"),
                Instant.parse("2026-11-08T19:30:00Z"),
                "admin-operator",
                "courier-operator",
                5L
        );

        byte[] serializedFull = serializer.serialize(fullResponse);
        assertThat(serializedFull).isNotEmpty();

        Object deserializedFullObj = serializer.deserialize(serializedFull);
        assertThat(deserializedFullObj)
                .as("El objeto deserializado debe ser una instancia de ShipmentResponse, no de Map")
                .isInstanceOf(ShipmentResponse.class);

        ShipmentResponse deserializedFull = (ShipmentResponse) deserializedFullObj;
        assertThat(deserializedFull).isEqualTo(fullResponse);
        assertThat(deserializedFull.origin()).isEqualTo(fullResponse.origin());
        assertThat(deserializedFull.destination()).isEqualTo(fullResponse.destination());
        assertThat(deserializedFull.courierId()).isEqualTo(fullResponse.courierId());
        assertThat(deserializedFull.status()).isEqualTo(ShipmentStatus.EN_RUTA);
        assertThat(deserializedFull.createdAt()).isEqualTo(Instant.parse("2026-11-08T18:00:00Z"));

        // Caso con courierId nullable (null)
        ShipmentResponse unassignedResponse = new ShipmentResponse(
                UUID.randomUUID(),
                "LT-NULLCOURIER",
                UUID.randomUUID(),
                new AddressDto("Av. San Martín", "456", "San Salvador de Jujuy", "4600"),
                new AddressDto("Belgrano", "789", "Salta", "4400"),
                new BigDecimal("1.50"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("10.00"),
                new BigDecimal("1000.00"),
                ShipmentStatus.PENDIENTE,
                null,
                Instant.parse("2026-11-08T20:00:00Z"),
                Instant.parse("2026-11-08T20:00:00Z"),
                "client-user",
                "client-user",
                1L
        );

        byte[] serializedUnassigned = serializer.serialize(unassignedResponse);
        Object deserializedUnassignedObj = serializer.deserialize(serializedUnassigned);

        assertThat(deserializedUnassignedObj).isInstanceOf(ShipmentResponse.class);
        ShipmentResponse deserializedUnassigned = (ShipmentResponse) deserializedUnassignedObj;
        assertThat(deserializedUnassigned.courierId()).isNull();
        assertThat(deserializedUnassigned).isEqualTo(unassignedResponse);
    }

    @Test
    @DisplayName("Configuración real de RedisCacheConfiguration: TTL 600s, null deshabilitado, namespace y serializadores")
    void redisCacheConfigurationMatchesProductionRequirements() {
        ObjectMapper objectMapper = CacheConfig.createCacheObjectMapper();
        RedisCacheConfiguration config = CacheConfig.createShipmentCacheConfiguration(objectMapper);

        // TTL fijo de 600 segundos (10 minutos)
        assertThat(config.getTtl())
                .isEqualTo(Duration.ofSeconds(600))
                .isEqualTo(Duration.ofMinutes(10));

        // Prohibido almacenar nulos
        assertThat(config.getAllowCacheNullValues()).isFalse();

        // Prefijo de namespace activado por defecto
        assertThat(config.usePrefix()).isTrue();
        assertThat(config.getKeyPrefixFor(CacheConfig.SHIPMENT_CACHE_NAME))
                .isEqualTo("shipment_queries::");

        // Serializador de claves como String
        assertThat(config.getKeySerializationPair().getReader())
                .isNotNull();
        java.nio.ByteBuffer keyBuf = config.getKeySerializationPair().write("shipment::id::123");
        String keyString = (String) config.getKeySerializationPair().read(keyBuf);
        assertThat(keyString).isEqualTo("shipment::id::123");

        // Serializador de valores tipado como ShipmentResponse
        assertThat(config.getValueSerializationPair().getReader())
                .isNotNull();
    }

    @Test
    @DisplayName("Contexto sin spring.cache.type configura RedisCacheManager transaccional con caché shipment_queries y TTL 600s")
    void defaultCacheTypeConfiguresTransactionalRedisCacheManagerWithTtl() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class, CacheConfig.class))
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheManager.class);
                    CacheManager cacheManager = context.getBean(CacheManager.class);
                    assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
                    RedisCacheManager redisCacheManager = (RedisCacheManager) cacheManager;
                    assertThat(redisCacheManager.isTransactionAware()).isTrue();

                    org.springframework.cache.Cache cache = redisCacheManager.getCache(CacheConfig.SHIPMENT_CACHE_NAME);
                    assertThat(cache).isNotNull();
                    assertThat(cache).isInstanceOf(org.springframework.cache.transaction.TransactionAwareCacheDecorator.class);
                    org.springframework.cache.Cache target =
                            ((org.springframework.cache.transaction.TransactionAwareCacheDecorator) cache).getTargetCache();
                    assertThat(target).isInstanceOf(org.springframework.data.redis.cache.RedisCache.class);
                    org.springframework.data.redis.cache.RedisCache redisCache =
                            (org.springframework.data.redis.cache.RedisCache) target;
                    assertThat(redisCache.getCacheConfiguration().getTtl()).isEqualTo(Duration.ofSeconds(600));
                });
    }

    @Test
    @DisplayName("Contexto con spring.cache.type=redis configura RedisCacheManager transaccional con TTL 600s")
    void redisPropertyExplicitlyConfiguresTransactionalRedisCacheManager() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class, CacheConfig.class))
                .withPropertyValues("spring.cache.type=redis")
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(CacheManager.class);
                    CacheManager cacheManager = context.getBean(CacheManager.class);
                    assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
                    RedisCacheManager redisCacheManager = (RedisCacheManager) cacheManager;
                    assertThat(redisCacheManager.isTransactionAware()).isTrue();

                    org.springframework.cache.Cache cache = redisCacheManager.getCache(CacheConfig.SHIPMENT_CACHE_NAME);
                    assertThat(cache).isNotNull();
                    assertThat(cache).isInstanceOf(org.springframework.cache.transaction.TransactionAwareCacheDecorator.class);
                    org.springframework.cache.Cache target =
                            ((org.springframework.cache.transaction.TransactionAwareCacheDecorator) cache).getTargetCache();
                    assertThat(target).isInstanceOf(org.springframework.data.redis.cache.RedisCache.class);
                    org.springframework.data.redis.cache.RedisCache redisCache =
                            (org.springframework.data.redis.cache.RedisCache) target;
                    assertThat(redisCache.getCacheConfiguration().getTtl()).isEqualTo(Duration.ofSeconds(600));
                });
    }

    @Test
    @DisplayName("Contexto con spring.cache.type=none configura NoOpCacheManager sin requerir Redis")
    void noneCacheTypeConfiguresNoOpCacheManagerWithoutRequiringRedis() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class, CacheConfig.class))
                .withPropertyValues("spring.cache.type=none")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(RedisCacheManager.class);
                    assertThat(context).doesNotHaveBean(RedisConnectionFactory.class);
                    assertThat(context).hasSingleBean(CacheManager.class);
                    CacheManager cacheManager = context.getBean(CacheManager.class);
                    assertThat(cacheManager).isInstanceOf(NoOpCacheManager.class);
                });
    }
}
