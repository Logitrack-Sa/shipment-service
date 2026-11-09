package com.logitrack.shipment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.dto.ShipmentResponse;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Slf4j
@Configuration
@EnableCaching
public class RedisCacheConfig implements CachingConfigurer {

    public static final String SHIPMENT_QUERIES_CACHE = "shipment_queries";
    public static final Duration SHIPMENT_QUERIES_TTL = Duration.ofMinutes(10);

    @Bean
    public RedisCacheManagerBuilderCustomizer shipmentCacheCustomizer(ObjectMapper objectMapper) {
        var serializer = new Jackson2JsonRedisSerializer<>(objectMapper, ShipmentResponse.class);
        var cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(SHIPMENT_QUERIES_TTL)
                .disableCachingNullValues()
                .computePrefixWith(cacheName -> "")
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
        return builder -> builder
                .withCacheConfiguration(SHIPMENT_QUERIES_CACHE, cacheConfig)
                .transactionAware();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Error al recuperar clave '{}' de la caché '{}'. Degradando a consulta directa de BD: {}",
                        key, cache != null ? cache.getName() : "desconocida", exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Error al almacenar clave '{}' en caché '{}': {}",
                        key, cache != null ? cache.getName() : "desconocida", exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Error al desalojar clave '{}' de caché '{}': {}",
                        key, cache != null ? cache.getName() : "desconocida", exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Error al limpiar caché '{}': {}",
                        cache != null ? cache.getName() : "desconocida", exception.getMessage());
            }
        };
    }
}
