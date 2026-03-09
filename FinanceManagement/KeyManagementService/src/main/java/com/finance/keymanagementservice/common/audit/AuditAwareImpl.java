package com.finance.keymanagementservice.common.audit;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("auditAwareImpl")
public class AuditAwareImpl implements AuditorAware<String> {

    /**
     * Returns the current auditor of the application.
     *
     * @return the current auditor.
     */
//    @Override
//    public Optional<String> getCurrentAuditor() {
//        return Optional.of("USERS_MS");
//    }

    @Override
    public Optional<String> getCurrentAuditor() {
        // Get the current authentication object
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            // Cast the principal to Jwt to access token claims
            Jwt jwt = (Jwt) authentication.getPrincipal();

            // Extract the "preferred_username" claim from the token
            String preferredUsername = jwt.getClaimAsString("preferred_username");

            // Return the preferred username as the auditor
            return Optional.ofNullable(preferredUsername);
        }

        // Fallback if no token or claim is available
        return Optional.of("Anonymous");
    }
}