package com.finance.aiservice.service;

import com.finance.aiservice.dto.SePayWebhookEvent;
import com.finance.aiservice.util.DataSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Processes real-time transactions from SePay webhooks.
 *
 * Flow:
 * 1. Receive webhook event
 * 2. Decrypt sensitive fields
 * 3. Sanitize for AI processing
 * 4. Quick anomaly check (is this transaction suspicious?)
 * 5. If suspicious, trigger immediate alert
 * 6. Forward to Kafka for batch processing later
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RealTimeTransactionProcessor {

    private final DataSanitizer dataSanitizer;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Process real-time transaction from SePay webhook.
     */
    public void processRealtimeTransaction(SePayWebhookEvent event) {
        log.info("Processing real-time transaction: {}", event.event());

        try {
            // Step 1: Extract transaction data
            Map<String, Object> transactionData = event.data();
            String userId = (String) transactionData.get("userId");
            String transactionId = (String) transactionData.get("transactionId");

            // Step 2: Decrypt and sanitize sensitive fields
            Map<String, Object> sanitized = sanitizeTransactionData(transactionData);

            // Step 3: Quick real-time anomaly check
            boolean isSuspicious = quickAnomalyCheck(sanitized, userId);

            if (isSuspicious) {
                log.warn("SUSPICIOUS TRANSACTION DETECTED: {}", transactionId);
                // TODO: Trigger immediate push notification via NotificationService
                publishImmediateAlert(userId, transactionId, sanitized);
            }

            // Step 4: Publish to Kafka for batch processing
            kafkaTemplate.send("finance.transactions.realtime", userId, sanitized);

            log.info("Real-time transaction processed successfully: {}", transactionId);

        } catch (Exception e) {
            log.error("Error processing real-time transaction: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to process transaction", e);
        }
    }

    /**
     * Sanitize transaction data before AI processing.
     */
    private Map<String, Object> sanitizeTransactionData(Map<String, Object> data) {
        Map<String, Object> sanitized = new HashMap<>();

        // Copy non-sensitive fields
        sanitized.put("transactionId", data.get("transactionId"));
        sanitized.put("userId", data.get("userId"));
        sanitized.put("amount", data.get("amount"));
        sanitized.put("currency", data.get("currency"));
        sanitized.put("category", data.get("category"));
        sanitized.put("transactionTime", data.get("transactionTime"));

        // Sanitize sensitive fields
        String accountNumber = (String) data.get("accountNumber");
        if (accountNumber != null) {
            sanitized.put("accountNumber", dataSanitizer.maskAccountNumber(accountNumber));
        }

        String merchantName = (String) data.get("merchantName");
        if (merchantName != null) {
            sanitized.put("merchantName", dataSanitizer.maskMerchantName(merchantName));
        }

        return sanitized;
    }

    /**
     * Quick rule-based anomaly check for real-time alerting.
     *
     * Checks:
     * - Amount exceeds threshold (e.g., > 10M VND)
     * - Transaction at unusual hour (2-5 AM)
     * - Multiple transactions in short time (potential card fraud)
     *
     * This is a fast heuristic check. Full AI analysis happens in batch.
     */
    private boolean quickAnomalyCheck(Map<String, Object> transaction, String userId) {
        // Check 1: High amount
        Number amount = (Number) transaction.get("amount");
        if (amount != null && amount.doubleValue() > 10_000_000) {
            log.info("High amount detected: {} VND", amount);
            return true;
        }

        // Check 2: Unusual hour
        // TODO: Implement time-based check

        // Check 3: Frequency check
        // TODO: Check Redis for recent transaction count

        return false;
    }

    /**
     * Publish immediate alert for suspicious transaction.
     */
    private void publishImmediateAlert(String userId, String transactionId, Map<String, Object> transaction) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("alertType", "SUSPICIOUS_TRANSACTION");
        alert.put("userId", userId);
        alert.put("transactionId", transactionId);
        alert.put("amount", transaction.get("amount"));
        alert.put("timestamp", java.time.LocalDateTime.now().toString());
        alert.put("severity", "HIGH");

        kafkaTemplate.send("ai.alerts.immediate", userId, alert);
        log.info("Immediate alert published for user {}", userId);
    }
}
