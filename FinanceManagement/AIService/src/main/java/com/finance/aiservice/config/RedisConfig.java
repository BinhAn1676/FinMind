package com.finance.aiservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis Configuration for AIService.
 *
 * Configures RedisTemplate for reading category cache from Redis.
 * AIService reads from cache written by FinanceService.
 *
 * CRITICAL: Uses the configured ObjectMapper (with JavaTimeModule) to ensure
 * serialization compatibility with FinanceService's RedisCacheConfig.
 */
@Configuration
public class RedisConfig {

    /**
     * Configure RedisTemplate for reading cached categories.
     *
     * Serialization must match FinanceService's RedisCacheConfig:
     * - Keys: String serializer (e.g., "userCategories::1")
     * - Values: JSON serializer with JavaTimeModule (Category objects with LocalDateTime)
     *
     * IMPORTANT: Uses the configured ObjectMapper to properly deserialize
     * LocalDateTime fields in Category objects cached by FinanceService.
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory redisConnectionFactory,
            ObjectMapper objectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // Use String serializer for keys
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Use JSON serializer with configured ObjectMapper (includes JavaTimeModule)
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
