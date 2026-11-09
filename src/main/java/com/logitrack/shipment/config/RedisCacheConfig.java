package com.logitrack.shipment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Configuración de Spring Cache respaldada por Redis para la caché de repartidores
 * disponibles (issue #108).
 *
 * <p><strong>Reglas de seguridad:</strong> esta clase únicamente configura la capa de caché;
 * <em>no</em> desactiva Spring Security, no salta filtros de autenticación ni elude las
 * validaciones de {@code JwtAuthenticationToken} / {@code ShipmentAccess}.</p>
 *
 * <p><strong>Regla de desacoplamiento:</strong> no importa clases de los paquetes
 * {@code client} ni {@code security} — la caché depende solo del dominio y de la
 * infraestructura Redis.</p>
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CourierCacheProperties.class)
public class RedisCacheConfig {

    /** Nombre lógico de la caché que almacena la disponibilidad de repartidores. */
    public static final String COURIERS_AVAILABLE = "couriers_available";

    /**
     * Construye el {@link RedisCacheManager} con:
     * <ul>
     *   <li>Serialización JSON para claves (String) y valores (Jackson genérico).</li>
     *   <li>TTL configurable vía {@link CourierCacheProperties#ttlSeconds()} (SDD 2.2.3).</li>
     *   <li>Estadísticas habilitadas para facilitar la verificación de hit/miss en tests.</li>
     *   <li>Nombre de caché {@value #COURIERS_AVAILABLE}.</li>
     * </ul>
     *
     * @param connectionFactory fábrica de conexiones Lettuce inyectada por Spring Boot
     * @param props             propiedades TTL y prefijo de clave
     * @return gestor de caché configurado
     */
    @Bean
    public RedisCacheManager redisCacheManager(
            RedisConnectionFactory connectionFactory,
            CourierCacheProperties props) {

        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        var valueSerializer = GenericJackson2JsonRedisSerializer.builder()
                .objectMapper(mapper)
                .defaultTyping(true)
                .build();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(props.ttlSeconds()))
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer))
                .computePrefixWith(cacheName -> props.keyPrefix() + ":");

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(COURIERS_AVAILABLE, defaultConfig)
                .enableStatistics()
                .build();
    }
}
