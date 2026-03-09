package com.finance.financeservice.mongo.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "report_schedules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportSchedule {

    @Id
    private String id;

    private String userId;

    private String email;

    private String frequency; // "daily", "weekly", "monthly"

    private Integer hour; // 0-23 (hour of day to execute)

    private String cronExpression; // Quartz cron format: "0 0 9 * * ?" (Phase 4)

    // Web Notification Settings (Phase 5)
    private Boolean webNotificationEnabled; // Enable/disable web notifications (bell icon)
    private Boolean notifyOnSuccess; // Send notification when report is successfully sent
    private Boolean notifyOnFailure; // Send notification when report generation/sending fails

    private Map<String, Object> filters;

    private Boolean active;

    private LocalDateTime lastExecuted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
