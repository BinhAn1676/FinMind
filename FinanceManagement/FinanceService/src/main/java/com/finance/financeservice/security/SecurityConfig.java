package com.finance.financeservice.security;


import com.finance.financeservice.exception.CustomAccessDeniedHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Security filter chain for internal service-to-service calls.
     * These endpoints don't require OAuth2 authentication.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain internalApiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(
                        "/api/v1/transactions/summary",
                        "/api/v1/transactions/list",
                        "/api/v1/transactions",  // For AIService getTransactionHistory & analyzeCategory
                        "/api/v1/transactions/category/**",
                        "/api/v1/categories",  // For AIService getUserCategories
                        "/api/transactions/summary",
                        "/api/transactions/list",
                        "/api/transactions/category/**",
                        "/api/v1/sepay/webhooks/receive",  // SePay webhook callback (must be public)
                        "/api/v1/sepay/oauth2/callback",   // OAuth2 token exchange callback
                        "/actuator/**"
                )
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

        return http.build();
    }

    /**
     * Security filter chain for public endpoints that require OAuth2 authentication.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        http.exceptionHandling(ehc -> ehc.accessDeniedHandler(new CustomAccessDeniedHandler()));
        return http.build();
    }
}