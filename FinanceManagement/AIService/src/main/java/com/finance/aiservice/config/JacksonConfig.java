package com.finance.aiservice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson ObjectMapper Configuration for AIService.
 *
 * CRITICAL: This configuration MUST match FinanceService's JacksonConfig
 * to ensure Redis cache serialization compatibility.
 *
 * Both services must use the same JavaTimeModule settings for LocalDateTime fields.
 */
@Configuration
public class JacksonConfig {

    /**
     * Configure ObjectMapper with proper date/time handling.
     *
     * IMPORTANT: Must have JavaTimeModule to serialize/deserialize LocalDateTime
     * in CategoryDto objects stored in Redis cache by FinanceService.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Register JavaTimeModule for Java 8 date/time types (LocalDateTime, etc.)
        objectMapper.registerModule(new JavaTimeModule());

        // Configure serialization features
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Configure deserialization features
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);

        return objectMapper;
    }
}
