package com.logitrack.shipment.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.logitrack.shipment.dto.ShipmentResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Configuración de caché para consultas de envíos mediante Redis (Cache-Aside).
 *
 * <p>Define el {@link RedisCacheManager} transaccional y la configuración de la caché
 * {@value #SHIPMENT_CACHE_NAME} con un TTL fijo de 10 minutos, serialización de claves
 * como String y serialización JSON tipada para {@link ShipmentResponse}.</p>
 *
 * <h3>Espacio de nombres y claves lógicas vs físicas</h3>
 * <p>Las claves lógicas del dominio son:
 * <ul>
 *   <li>{@code shipment::id::{uuid}}</li>
 *   <li>{@code shipment::tracking::{trackingCode}}</li>
 * </ul>
 * Al almacenarse en Redis, el {@link RedisCacheConfiguration} incorpora automáticamente
 * el prefijo de namespace del nombre de la caché como clave física:
 * <ul>
 *   <li>{@code shipment_queries::shipment::id::{uuid}}</li>
 *   <li>{@code shipment_queries::shipment::tracking::{trackingCode}}</li>
 * </ul>
 * No se deshabilita este prefijo para evitar colisiones con otras cachés.</p>
 *
 * <h3>Propiedades suministradas por Config Server</h3>
 * <p>En entornos {@code dev} y {@code prod}, las siguientes propiedades deben ser provistas
 * por Spring Cloud Config Server o variables de entorno:
 * <ul>
 *   <li>{@code spring.data.redis.host} (o {@code REDIS_HOST})</li>
 *   <li>{@code spring.data.redis.port} (o {@code REDIS_PORT})</li>
 *   <li>{@code spring.data.redis.password} (o {@code REDIS_PASSWORD})</li>
 *   <li>{@code spring.data.redis.connect-timeout}</li>
 *   <li>{@code spring.data.redis.timeout}</li>
 * </ul>
 * </p>
 *
 * <h3>Limitaciones de consistencia de Cache-Aside</h3>
 * <ul>
 *   <li><b>Separación de operaciones de evicción:</b> Las evicciones de ID y tracking son operaciones
 *   Redis independientes, no atómicas entre sí. Un fallo operativo intermedio puede dejar una clave
 *   desalojada y la otra intacta en Redis.</li>
 *   <li><b>Atomicidad entre MySQL y Redis:</b> MySQL y Redis no forman una transacción 2PC (distribuida
 *   atómica). Si la evicción en Redis falla después de realizar el commit en MySQL, la mutación en MySQL
 *   no se revierte. Las excepciones de invalidación se mantienen visibles (sin silenciar) para detectar fallos operativos.</li>
 *   <li><b>Carreras de concurrencia y obsolescencia:</b> En el patrón Cache-Aside estándar, existe una ventana
 *   donde una lectura concurrente puede cargar un dato previo desde la base de datos y repoblar la caché después
 *   de que una mutación haya emitido la invalidación. Esta implementación no garantiza consistencia linealizable
 *   ni revocación instantánea bajo dichas carreras concurrentes; los escenarios de reasignación verifican ejecución secuencial.</li>
 *   <li><b>Cálculo del TTL:</b> El TTL fijo de 10 minutos (600 segundos) se computa estrictamente a partir de cada
 *   escritura individual en Redis, no a partir del momento de la mutación en MySQL.</li>
 *   <li><b>Impacto en la autorización:</b> Las decisiones de autorización en {@code ShipmentService} se evalúan
 *   a partir del {@code customerId} y {@code courierId} presentes en el {@link ShipmentResponse} retornado (sea desde
 *   caché o base de datos). Por lo tanto, durante una ventana de obsolescencia por repoblación concurrente, un repartidor
 *   anterior podría conservar acceso de lectura transitoriamente hasta la expiración de la clave.</li>
 * </ul>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String SHIPMENT_CACHE_NAME = "shipment_queries";
    public static final Duration SHIPMENT_CACHE_TTL = Duration.ofMinutes(10);

    /**
     * Construye el {@link ObjectMapper} tipado y configurado para soportar records,
     * {@link java.time.Instant}, {@link java.util.UUID}, {@link java.math.BigDecimal}
     * y DTOs anidados sin deserialización polimórfica indiscriminada.
     */
    public static ObjectMapper createCacheObjectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * Configuración de caché para consultas de envíos:
     * - TTL fijo de 10 minutos sin renovación en lectura.
     * - Rechazo de almacenamiento de nulos.
     * - Claves como String con prefijo de namespace activado por defecto.
     * - Valores serializados como JSON tipado a {@link ShipmentResponse}.
     */
    public static RedisCacheConfiguration createShipmentCacheConfiguration(ObjectMapper objectMapper) {
        Jackson2JsonRedisSerializer<ShipmentResponse> valueSerializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, ShipmentResponse.class);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(SHIPMENT_CACHE_TTL)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(valueSerializer));
    }

    /**
     * Administrador de caché Redis con soporte transaccional.
     * Se activa únicamente si {@code spring.cache.type=redis} (o si no está definida la propiedad)
     * y no existe otro bean {@link CacheManager} en el contexto.
     */
    @Bean
    @ConditionalOnMissingBean(CacheManager.class)
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis", matchIfMissing = true)
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = createCacheObjectMapper();
        RedisCacheConfiguration config = createShipmentCacheConfiguration(objectMapper);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .withCacheConfiguration(SHIPMENT_CACHE_NAME, config)
                .transactionAware()
                .build();
    }
}
