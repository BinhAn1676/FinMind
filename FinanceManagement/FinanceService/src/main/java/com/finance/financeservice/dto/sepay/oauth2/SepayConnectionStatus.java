package com.finance.financeservice.dto.sepay.oauth2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO representing user's SePay connection status for frontend display.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SepayConnectionStatus {
    /** Whether OAuth2 mode is enabled (feature flag) */
    private boolean oauth2Enabled;

    /** Whether the user has connected their SePay account via OAuth2 */
    private boolean connected;

    /** Scopes granted */
    private String scopes;

    /** When the connection was established */
    private LocalDateTime connectedAt;

    /** Whether the access token is still valid */
    private boolean tokenValid;

    /** URL for OAuth2 authorization (to initiate connection) */
    private String authorizeUrl;
}
