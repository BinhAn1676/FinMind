package com.finance.financeservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.CacheManager;
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
 * Redis Cache Configuration for FinanceService.
 *
 * Caches user categories for fast access from AIService.
 * Categories are cached per userId with automatic eviction on updates.
 */
@Configuration
@EnableCaching
public class RedisCacheConfig {

    /**
     * Configure Redis Cache Manager with category-specific settings.
     *
     * Cache Configuration:
     * - userCategories: TTL 24 hours, auto-evict on category add/delete
     * - Key format: userCategories::userId
     * - Value: JSON serialized list of Category objects
     *
     * IMPORTANT: Uses the configured ObjectMapper (with JavaTimeModule) to properly
     * serialize LocalDateTime fields in Category objects.
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory, ObjectMapper objectMapper) {
        // Use configured ObjectMapper with JavaTimeModule for proper LocalDateTime serialization
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofHours(24))  // Cache expires after 24 hours
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer)
                )
                .disableCachingNullValues();  // Don't cache null values

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .build();
    }
}
