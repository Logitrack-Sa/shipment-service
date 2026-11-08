package com.logitrack.shipment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.logitrack.shipment.dto.ShipmentResponse;
import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
@EnableCaching
public class RedisCacheConfig {

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
}
