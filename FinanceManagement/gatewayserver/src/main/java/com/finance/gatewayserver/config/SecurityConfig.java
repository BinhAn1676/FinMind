package com.finance.gatewayserver.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity) {
        serverHttpSecurity.authorizeExchange(exchanges -> exchanges/*.pathMatchers().permitAll()*/
                        .pathMatchers("/actuator/**", "/actuator/prometheus").permitAll()
                        // CORS preflight requests must not require auth
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // SePay webhook callback - must be public for SePay to send events
                        .pathMatchers(HttpMethod.POST, "/finances/api/v1/sepay/webhooks/receive").permitAll()
                        // Config Server monitor endpoint for GitHub webhook
                        .pathMatchers(HttpMethod.POST, "/configserver/monitor").permitAll()
                        // WebSocket SockJS handshake endpoints - must be public (JWT is sent in STOMP headers, not HTTP)
                        .pathMatchers("/chat/ws/**").permitAll()
                        .pathMatchers("/notify/ws/**").permitAll()
                        //.pathMatchers("/users/**").hasRole("USER")
                        //.pathMatchers("/finances/**").hasRole("FINANCE")
                        .anyExchange().authenticated()
                       )
                .oauth2ResourceServer(oAuth2ResourceServerSpec -> oAuth2ResourceServerSpec
                        .jwt(jwtSpec -> jwtSpec.jwtAuthenticationConverter(grantedAuthoritiesExtractor())))
                .cors(corsSpec -> corsSpec.configurationSource(corsConfigurationSource()));
        serverHttpSecurity.csrf(csrfSpec -> csrfSpec.disable());
        return serverHttpSecurity.build();
    }
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration corsConfig = new CorsConfiguration();
        // When using allowCredentials(true), you can't use "*" for origins
        corsConfig.setAllowedOriginPatterns(Arrays.asList(
                "http://localhost:[*]",
                "https://finmind.pro.vn"
        ));
        corsConfig.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        corsConfig.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-Requested-With"));
        corsConfig.setAllowCredentials(true);
        corsConfig.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", corsConfig);
        return source;
    }


    private Converter<Jwt, Mono<AbstractAuthenticationToken>> grantedAuthoritiesExtractor() {
        JwtAuthenticationConverter jwtAuthenticationConverter =
                new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter
                (new KeycloakRoleConverter());
        return new ReactiveJwtAuthenticationConverterAdapter(jwtAuthenticationConverter);
    }

}