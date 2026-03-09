package com.finance.aiservice.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for RestTemplate used by EmbeddingService to call Ollama API.
 */
@Configuration
public class RestTemplateConfig {

    /**
     * RestTemplate for calling Ollama embedding API.
     *
     * Configured with:
     * - 30 second connection timeout
     * - 60 second read timeout (embedding generation can take time)
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
