package com.finance.financeservice.controller;

import com.finance.financeservice.config.SepayOAuth2Properties;
import com.finance.financeservice.dto.sepay.SyncResult;
import com.finance.financeservice.dto.sepay.oauth2.SepayConnectionStatus;
import com.finance.financeservice.service.SepayOAuth2TokenService;
import com.finance.financeservice.service.SepayWebhookService;
import com.finance.financeservice.service.SepaySyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for SePay OAuth2 integration.
 * Handles OAuth2 authorization callbacks, connection management, and webhook setup.
 */
@RestController
@RequestMapping("/api/v1/sepay/oauth2")
@RequiredArgsConstructor
@Slf4j
public class SepayOAuth2Controller {

    private final SepayOAuth2TokenService tokenService;
    private final SepayWebhookService webhookService;
    private final SepayOAuth2Properties oauth2Properties;
    private final SepaySyncService sepaySyncService;

    /**
     * Get the SePay OAuth2 connection status for a user.
     * Returns whether OAuth2 is enabled, connection status, and authorize URL.
     */
    @GetMapping("/status")
    public ResponseEntity<SepayConnectionStatus> getConnectionStatus(@RequestParam("userId") String userId) {
        return ResponseEntity.ok(tokenService.getConnectionStatus(userId));
    }

    /**
     * Get the authorization URL to redirect the user to SePay for OAuth2 consent.
     */
    @GetMapping("/authorize-url")
    public ResponseEntity<Map<String, String>> getAuthorizeUrl(@RequestParam("userId") String userId) {
        if (!oauth2Properties.isEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("error", "OAuth2 is not enabled"));
        }
        String url = tokenService.buildAuthorizeUrl(userId);
        return ResponseEntity.ok(Map.of("authorizeUrl", url));
    }

    /**
     * Exchange the authorization code for tokens.
     * Called by the frontend after SePay redirects back with the code.
     */
    @PostMapping("/callback")
    public ResponseEntity<SepayConnectionStatus> handleCallback(
            @RequestParam("code") String code,
            @RequestParam("userId") String userId,
            @RequestParam(value = "state", required = false) String state) {

        if (!oauth2Properties.isEnabled()) {
            return ResponseEntity.badRequest().body(SepayConnectionStatus.builder()
                    .oauth2Enabled(false).connected(false).build());
        }

        log.info("Processing OAuth2 callback for userId: {}", userId);

        // Exchange code for tokens
        SepayConnectionStatus status = tokenService.exchangeCodeForTokens(code, userId);

        if (status.isConnected()) {
            // Sync accounts first, then create webhooks for real-time transaction updates
            try {
                sepaySyncService.syncAccountsForUserOAuth2(userId);
                webhookService.createWebhooksForUser(userId);
                log.info("Accounts synced and webhooks created for userId: {}", userId);
            } catch (Exception e) {
                log.error("Error during post-connection setup for userId: {}", userId, e);
            }
        }

        return ResponseEntity.ok(status);
    }

    /**
     * Disconnect a user's SePay OAuth2 connection.
     * Removes tokens and deletes webhooks from SePay.
     */
    @PostMapping("/disconnect")
    public ResponseEntity<Map<String, String>> disconnect(@RequestParam("userId") String userId) {
        if (!oauth2Properties.isEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("error", "OAuth2 is not enabled"));
        }

        try {
            // Delete webhooks first (needs valid token)
            webhookService.deleteWebhooksForUser(userId);
        } catch (Exception e) {
            log.error("Error deleting webhooks during disconnect for userId: {}", userId, e);
        }

        // Then disconnect (removes tokens)
        tokenService.disconnect(userId);

        return ResponseEntity.ok(Map.of("message", "Disconnected successfully"));
    }

    /**
     * Manually trigger account sync using OAuth2 tokens.
     */
    @PostMapping("/sync/accounts")
    public ResponseEntity<SyncResult> syncAccountsOAuth2(@RequestParam("userId") String userId) {
        if (!oauth2Properties.isEnabled()) {
            return ResponseEntity.badRequest().body(SyncResult.builder()
                    .success(false).message("OAuth2 is not enabled").processedCount(0).build());
        }
        return ResponseEntity.ok(sepaySyncService.syncAccountsForUserOAuth2Api(userId));
    }

    /**
     * Manually trigger transaction sync using OAuth2 tokens.
     */
    @PostMapping("/sync/transactions")
    public ResponseEntity<SyncResult> syncTransactionsOAuth2(@RequestParam("userId") String userId) {
        if (!oauth2Properties.isEnabled()) {
            return ResponseEntity.badRequest().body(SyncResult.builder()
                    .success(false).message("OAuth2 is not enabled").processedCount(0).build());
        }
        return ResponseEntity.ok(sepaySyncService.syncTransactionsForUserOAuth2Api(userId));
    }

    /**
     * Manually recreate webhooks for a user (if some were deleted or need refresh).
     */
    @PostMapping("/webhooks/setup")
    public ResponseEntity<Map<String, String>> setupWebhooks(@RequestParam("userId") String userId) {
        if (!oauth2Properties.isEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("error", "OAuth2 is not enabled"));
        }
        try {
            webhookService.createWebhooksForUser(userId);
            return ResponseEntity.ok(Map.of("message", "Webhooks setup completed"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
