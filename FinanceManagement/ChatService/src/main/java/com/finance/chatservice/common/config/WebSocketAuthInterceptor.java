package com.finance.chatservice.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * WebSocket Channel Interceptor to store JWT token in session attributes.
 *
 * This interceptor captures the JWT token from the SecurityContext during CONNECT
 * and stores it in the WebSocket session attributes for later retrieval.
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String JWT_TOKEN_ATTRIBUTE = "JWT_TOKEN";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = null;

            // Try to extract JWT from multiple sources

            // 1. Try from Authorization header in STOMP
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7);
                log.debug("🔑 Found JWT in Authorization header");
            }

            // 2. Try from custom X-Authorization header
            if (token == null) {
                String xAuthHeader = accessor.getFirstNativeHeader("X-Authorization");
                if (xAuthHeader != null) {
                    token = xAuthHeader.startsWith("Bearer ") ? xAuthHeader.substring(7) : xAuthHeader;
                    log.debug("🔑 Found JWT in X-Authorization header");
                }
            }

            // 3. Try from Authentication object (if JWT was validated during handshake)
            if (token == null) {
                Authentication authentication = (Authentication) accessor.getUser();
                if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
                    Jwt jwt = (Jwt) authentication.getPrincipal();
                    token = jwt.getTokenValue();
                    log.debug("🔑 Found JWT in Authentication");
                }
            }

            if (token != null) {
                // Store JWT token in session attributes for later retrieval
                accessor.getSessionAttributes().put(JWT_TOKEN_ATTRIBUTE, token);
                log.info("✅ WebSocket CONNECT: Stored JWT token in session (length: {})", token.length());
            } else {
                log.warn("⚠️ WebSocket CONNECT: No JWT token found in headers or authentication");
            }
        }

        return message;
    }

    /**
     * Retrieve JWT token from WebSocket session attributes.
     *
     * @param sessionAttributes The WebSocket session attributes map
     * @return JWT token string, or null if not found
     */
    public static String getJwtTokenFromSession(java.util.Map<String, Object> sessionAttributes) {
        if (sessionAttributes != null) {
            return (String) sessionAttributes.get(JWT_TOKEN_ATTRIBUTE);
        }
        return null;
    }
}
