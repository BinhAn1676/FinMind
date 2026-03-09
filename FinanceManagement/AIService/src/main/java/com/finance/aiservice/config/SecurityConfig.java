package com.finance.aiservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for AIService.
 *
 * Internal endpoints (/api/ai/internal/**) are accessible without authentication
 * for service-to-service communication.
 *
 * Public endpoints and actuator endpoints are also accessible.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Internal endpoints - no authentication required (service-to-service)
                .requestMatchers("/api/ai/internal/**").permitAll()

                // Actuator endpoints - no authentication required (for monitoring)
                .requestMatchers("/actuator/**").permitAll()

                // Health check - no authentication required
                .requestMatchers("/api/ai/health").permitAll()

                // All other endpoints require authentication
                .anyRequest().permitAll()
            )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .csrf(csrf -> csrf.disable()); // Disable CSRF for stateless REST API

        return http.build();
    }
}
