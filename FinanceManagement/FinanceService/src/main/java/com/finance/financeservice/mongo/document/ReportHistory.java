package com.finance.financeservice.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Represents a history record of executed scheduled reports
 * Phase 5: Report History & Web Notifications
 */
@Document(collection = "report_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportHistory {

    @Id
    private String id;

    private String userId;

    private String scheduleId; // Reference to ReportSchedule

    private String email; // Email address the report was sent to

    private String reportType; // "daily", "weekly", "monthly", "custom"

    private String exportFormat; // "excel", "pdf", "csv"

    private Integer transactionCount; // Number of transactions in the report

    private Double totalAmount; // Total amount (for expense summary)

    private LocalDateTime executedAt; // When the report was generated

    private String status; // "success", "failed"

    private String errorMessage; // Error message if status = "failed"

    private Boolean emailSent; // Whether email was successfully sent

    private Boolean notificationSent; // Whether web notification was successfully sent

    private Map<String, Object> filters; // Filters used to generate the report

    private LocalDateTime createdAt;
}
