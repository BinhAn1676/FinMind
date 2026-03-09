package com.finance.aiservice.config;

import feign.Logger;
import feign.RequestInterceptor;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Feign Client Configuration for AIService.
 *
 * Enables Feign clients to communicate with other microservices:
 * - FinanceService (transaction data)
 * - UserService (user profiles)
 * - KeyManagementService (encryption keys)
 *
 * IMPORTANT: Forwards JWT tokens from incoming requests to outgoing Feign calls
 * to maintain authentication context across microservices.
 */
@Configuration
@EnableFeignClients(basePackages = "com.finance.aiservice.client")
public class FeignConfig {

    /**
     * Feign logging level for debugging.
     * FULL = log headers, body, and metadata for request and response
     */
    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /**
     * JWT Token Forwarding Interceptor.
     *
     * Extracts the JWT token from the current SecurityContext and adds it
     * to the Authorization header of all Feign requests.
     *
     * This ensures that when AIService calls other services (FinanceService, etc.),
     * the user's authentication token is propagated.
     */
    @Bean
    public RequestInterceptor jwtTokenForwardingInterceptor() {
        return requestTemplate -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

            if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
                // Extract JWT token value
                String tokenValue = jwt.getTokenValue();

                // Add Authorization header with Bearer token
                requestTemplate.header("Authorization", "Bearer " + tokenValue);
            }
        };
    }
}
