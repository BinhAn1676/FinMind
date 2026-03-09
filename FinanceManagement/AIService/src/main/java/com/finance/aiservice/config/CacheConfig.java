package com.finance.aiservice.config;

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
 * Redis Cache Configuration for AIService.
 *
 * Caches:
 * - insightCards: AI-generated insight cards (TTL: 24 hours)
 * - aiResponses: AI chat responses (TTL: 1 hour)
 * - transactionSummaries: Fetched from FinanceService (TTL: 30 minutes)
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Configure Redis cache manager with custom TTL per cache.
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Default cache configuration
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofHours(1))  // Default: 1 hour
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new StringRedisSerializer()
                )
            )
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                    new GenericJackson2JsonRedisSerializer()
                )
            )
            .disableCachingNullValues();

        // Specific cache configurations
        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withCacheConfiguration("insightCards",
                defaultConfig.entryTtl(Duration.ofHours(24)))  // 24 hours
            .withCacheConfiguration("aiResponses",
                defaultConfig.entryTtl(Duration.ofHours(1)))   // 1 hour
            .withCacheConfiguration("transactionSummaries",
                defaultConfig.entryTtl(Duration.ofMinutes(30))) // 30 minutes
            .build();
    }
}
