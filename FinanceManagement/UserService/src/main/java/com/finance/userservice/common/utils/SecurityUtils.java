package com.finance.userservice.common.utils;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    /**
     * Gets the username of the currently authenticated user
     * @return the username, or null if not authenticated
     */
    public static String getCurrentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getClaimAsString("preferred_username");
        }

        return null;
    }

    /**
     * Attempts to extract the numeric user id from JWT claims.
     * Supports common claim keys such as "user_id" and "sub".
     * @return the user id as Long, or null if unavailable / unparsable
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }

        Object userIdClaim = jwt.getClaims().get("user_id");
        if (userIdClaim == null) {
            userIdClaim = jwt.getClaims().get("sub");
        }

        if (userIdClaim instanceof Number number) {
            return number.longValue();
        }

        if (userIdClaim instanceof String str) {
            try {
                return Long.parseLong(str);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }

        return null;
    }
}