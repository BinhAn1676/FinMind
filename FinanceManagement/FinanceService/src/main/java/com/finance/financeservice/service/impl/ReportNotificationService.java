package com.finance.financeservice.service.impl;

import com.finance.financeservice.common.config.KafkaConfig;
import com.finance.financeservice.event.UserNotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Service for sending web notifications about scheduled reports via Kafka
 * Phase 5: Report History & Web Notifications (replaces SMS)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReportNotificationService {

    private final KafkaTemplate<String, UserNotificationEvent> notificationKafkaTemplate;

    /**
     * Send web notification when report is successfully generated and sent
     * @param userId User ID
     * @param email User email (for display)
     * @param reportType Report type (daily, weekly, monthly, custom)
     * @param transactionCount Number of transactions in the report
     * @param totalAmount Total amount in the report (for summary)
     * @return true if notification was sent successfully, false otherwise
     */
    public boolean sendReportReadyNotification(Long userId, String email, String reportType, int transactionCount, double totalAmount) {
        if (userId == null) {
            log.warn("⚠️ User ID is null, skipping notification");
            return false;
        }

        try {
            String title = "📊 Finance Report Ready!";
            String message = buildReportReadyMessage(reportType, transactionCount, totalAmount);

            Map<String, String> additionalData = new HashMap<>();
            additionalData.put("reportType", reportType);
            additionalData.put("transactionCount", String.valueOf(transactionCount));
            additionalData.put("totalAmount", String.format("%.0f", totalAmount));

            UserNotificationEvent event = UserNotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("REPORT_SUCCESS")
                    .userId(userId)
                    .email(email)
                    .title(title)
                    .message(message)
                    .source("FinanceService")
                    .timestamp(LocalDateTime.now())
                    .additionalData(additionalData)
                    .build();

            notificationKafkaTemplate.send(KafkaConfig.USER_NOTIFICATION_TOPIC, String.valueOf(userId), event);
            log.info("✅ Report ready notification sent to user {}", userId);
            return true;
        } catch (Exception e) {
            log.error("❌ Failed to send report ready notification: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Send web notification when report generation/sending fails
     * @param userId User ID
     * @param email User email (for display)
     * @param errorMessage Error description
     */
    public void sendReportFailedNotification(Long userId, String email, String errorMessage) {
        if (userId == null) {
            log.warn("⚠️ User ID is null, skipping notification");
            return;
        }

        try {
            String title = "⚠️ Finance Report Failed";
            String message = buildReportFailedMessage(errorMessage);

            Map<String, String> additionalData = new HashMap<>();
            additionalData.put("errorMessage", errorMessage != null ? errorMessage : "Unknown error");

            UserNotificationEvent event = UserNotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("REPORT_FAILURE")
                    .userId(userId)
                    .email(email)
                    .title(title)
                    .message(message)
                    .source("FinanceService")
                    .timestamp(LocalDateTime.now())
                    .additionalData(additionalData)
                    .build();

            notificationKafkaTemplate.send(KafkaConfig.USER_NOTIFICATION_TOPIC, String.valueOf(userId), event);
            log.info("✅ Report failed notification sent to user {}", userId);
        } catch (Exception e) {
            log.error("❌ Failed to send report failed notification: {}", e.getMessage(), e);
        }
    }

    /**
     * Build message for successful report notification
     */
    private String buildReportReadyMessage(String reportType, int transactionCount, double totalAmount) {
        return String.format(
            "Your %s report has been sent to your email.\n\n" +
            "Summary:\n" +
            "• Transactions: %d\n" +
            "• Total: %,.0f VNĐ\n\n" +
            "Check your email for the full report.",
            capitalize(reportType),
            transactionCount,
            totalAmount
        );
    }

    /**
     * Build message for failed report notification
     */
    private String buildReportFailedMessage(String errorMessage) {
        return String.format(
            "Your scheduled report could not be generated.\n\n" +
            "Error: %s\n\n" +
            "Please check your schedule settings or contact support.",
            errorMessage != null ? errorMessage : "Unknown error"
        );
    }

    /**
     * Capitalize first letter of string
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
