package com.finance.aiservice.controller;

import com.finance.aiservice.dto.SePayWebhookEvent;
import com.finance.aiservice.service.RealTimeTransactionProcessor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhook controller for receiving real-time transaction notifications from SePay.
 *
 * Flow:
 * 1. SePay POSTs transaction event to this endpoint
 * 2. Validate webhook signature (security)
 * 3. Decrypt and sanitize transaction data
 * 4. Process with AI for real-time anomaly detection
 * 5. Publish to Kafka if needed
 * 6. Return 200 OK to SePay
 *
 * Endpoint: POST /api/ai/webhooks/sepay
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/webhooks")
@RequiredArgsConstructor
public class SePayWebhookController {

    private final RealTimeTransactionProcessor transactionProcessor;

    /**
     * Receive SePay webhook for real-time transaction events.
     *
     * SePay Webhook Format:
     * {
     *   "event": "transaction.created",
     *   "timestamp": "2026-01-21T10:30:00Z",
     *   "data": {
     *     "transactionId": "txn-abc123",
     *     "userId": "user-12345",
     *     "amount": 500000,
     *     "currency": "VND",
     *     "merchantName": "STARBUCKS COFFEE",
     *     "category": "DINING",
     *     "accountNumber": "1234567890",
     *     "transactionTime": "2026-01-21T10:25:00Z"
     *   },
     *   "signature": "sha256_hmac_signature"
     * }
     *
     * Security:
     * - Validate HMAC signature to ensure request is from SePay
     * - Reject if signature is invalid
     *
     * @param event SePay webhook event
     * @param signature HMAC signature from SePay-Signature header
     * @return 200 OK if processed, 400/401 if invalid
     */
    @PostMapping("/sepay")
    public ResponseEntity<WebhookResponse> handleSePayWebhook(
        @Valid @RequestBody SePayWebhookEvent event,
        @RequestHeader(value = "SePay-Signature", required = false) String signature
    ) {
        log.info("Received SePay webhook: event={}, transactionId={}",
            event.event(), event.data().get("transactionId"));

        try {
            // Step 1: Validate signature
            if (!validateSignature(event, signature)) {
                log.warn("Invalid webhook signature from SePay");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(WebhookResponse.error("Invalid signature"));
            }

            // Step 2: Process transaction in real-time
            transactionProcessor.processRealtimeTransaction(event);

            // Step 3: Return success to SePay
            return ResponseEntity.ok(WebhookResponse.success("Transaction processed"));

        } catch (Exception e) {
            log.error("Error processing SePay webhook: {}", e.getMessage(), e);

            // Return 500 so SePay will retry
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(WebhookResponse.error("Processing failed"));
        }
    }

    /**
     * Validate HMAC signature from SePay.
     *
     * Algorithm:
     * 1. Compute HMAC-SHA256 of request body using shared secret
     * 2. Compare with signature from header
     * 3. Use constant-time comparison to prevent timing attacks
     */
    private boolean validateSignature(SePayWebhookEvent event, String signature) {
        if (signature == null || signature.isBlank()) {
            log.warn("Missing SePay-Signature header");
            return false;
        }

        // TODO: Implement actual HMAC validation
        // String expectedSignature = computeHmac(event, SEPAY_WEBHOOK_SECRET);
        // return MessageDigest.isEqual(signature.getBytes(), expectedSignature.getBytes());

        // For now, accept all (DEVELOPMENT ONLY)
        log.warn("Webhook signature validation is disabled (DEV MODE)");
        return true;
    }

    /**
     * Webhook response DTO.
     */
    public record WebhookResponse(
        boolean success,
        String message,
        String timestamp
    ) {
        public static WebhookResponse success(String message) {
            return new WebhookResponse(
                true,
                message,
                java.time.LocalDateTime.now().toString()
            );
        }

        public static WebhookResponse error(String message) {
            return new WebhookResponse(
                false,
                message,
                java.time.LocalDateTime.now().toString()
            );
        }
    }

    /**
     * Health check for webhook endpoint.
     */
    @GetMapping("/sepay/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("SePay webhook endpoint is healthy");
    }
}
