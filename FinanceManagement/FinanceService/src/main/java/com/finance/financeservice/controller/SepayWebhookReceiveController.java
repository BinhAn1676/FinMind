package com.finance.financeservice.controller;

import com.finance.financeservice.dto.sepay.oauth2.SepayWebhookEvent;
import com.finance.financeservice.service.sepay.SepayWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller that receives webhook events from SePay.
 * SePay sends a POST request to this endpoint whenever a transaction occurs.
 *
 * This endpoint must be publicly accessible (no JWT auth required)
 * so that SePay can call it. Authentication is handled by webhook auth type
 * (API Key, OAuth2, or No Auth) configured when creating the webhook.
 */
@RestController
@RequestMapping("/api/v1/sepay/webhooks")
@RequiredArgsConstructor
@Slf4j
public class SepayWebhookReceiveController {

    private final SepayWebhookService webhookService;

    /**
     * Receive a webhook event from SePay.
     * Must respond with 2xx status code to indicate successful receipt.
     * If non-2xx is returned, SePay will retry.
     */
    @PostMapping("/receive")
    public ResponseEntity<Map<String, String>> receiveWebhook(@RequestBody SepayWebhookEvent event) {
        log.info("Received SePay webhook event: id={}, gateway={}, transferType={}, amount={}",
                event.getId(), event.getGateway(), event.getTransferType(), event.getTransferAmount());

        try {
            webhookService.processWebhookEvent(event);
            return ResponseEntity.ok(Map.of("status", "success"));
        } catch (Exception e) {
            log.error("Error processing SePay webhook event", e);
            // Still return 200 to prevent SePay from retrying
            // (we log the error and can investigate later)
            return ResponseEntity.ok(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
