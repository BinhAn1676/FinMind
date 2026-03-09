package com.finance.financeservice.service.impl;

import com.finance.financeservice.mongo.document.ReportHistory;
import com.finance.financeservice.mongo.document.ReportSchedule;
import com.finance.financeservice.mongo.document.Transaction;
import com.finance.financeservice.mongo.repository.ReportHistoryRepository;
import com.finance.financeservice.mongo.repository.ReportScheduleRepository;
import com.finance.financeservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "spring.mail.host")
public class ScheduledReportService {

    private final ReportScheduleRepository scheduleRepository;
    private final ReportHistoryRepository historyRepository; // Phase 5: Report history tracking
    private final TransactionService transactionService;
    private final ExcelExportService excelExportService;
    private final PDFExportService pdfExportService;
    private final CSVExportService csvExportService;
    private final EmailService emailService;
    private final ReportNotificationService reportNotificationService; // Phase 5: Web notifications

    // Run every minute to support custom cron schedules with minute-level precision
    // Phase 4: Changed from "0 0 * * * *" (every hour) to support minute-level cron expressions
    @Scheduled(cron = "0 * * * * *")
    public void processScheduledReports() {
        log.info("⏰ Checking for scheduled reports to execute...");

        List<ReportSchedule> activeSchedules = scheduleRepository.findByActiveTrue();
        log.info("📊 Found {} active schedule(s)", activeSchedules.size());

        LocalDateTime now = LocalDateTime.now();

        for (ReportSchedule schedule : activeSchedules) {
            try {
                if (shouldExecuteSchedule(schedule, now)) {
                    executeSchedule(schedule);
                    schedule.setLastExecuted(now);
                    scheduleRepository.save(schedule);
                }
            } catch (Exception e) {
                log.error("❌ Failed to execute schedule {}: {}", schedule.getId(), e.getMessage(), e);

                // Phase 5: Send web notification on failure
                boolean notificationSent = false;
                if (schedule.getWebNotificationEnabled() != null && schedule.getWebNotificationEnabled()
                    && schedule.getNotifyOnFailure() != null && schedule.getNotifyOnFailure()) {
                    try {
                        Long userId = Long.parseLong(schedule.getUserId());
                        reportNotificationService.sendReportFailedNotification(
                            userId,
                            schedule.getEmail(),
                            "Report generation failed. Please check your schedule settings."
                        );
                        notificationSent = true;
                    } catch (NumberFormatException nfe) {
                        log.error("❌ Invalid userId format: {}", schedule.getUserId());
                    }
                }

                // Phase 5: Save failed report history
                String reportType = schedule.getCronExpression() != null ? "custom" :
                                   (schedule.getFrequency() != null ? schedule.getFrequency() : "scheduled");
                String exportFormat = schedule.getFilters() != null ?
                                     (String) schedule.getFilters().getOrDefault("exportFormat", "excel") : "excel";

                saveReportHistory(schedule, exportFormat, 0, 0.0, reportType, false,
                                 e.getMessage(), false, notificationSent);
            }
        }
    }

    private boolean shouldExecuteSchedule(ReportSchedule schedule, LocalDateTime now) {
        // Phase 4: Check for cron expression first (new scheduling mode)
        if (schedule.getCronExpression() != null && !schedule.getCronExpression().trim().isEmpty()) {
            return shouldExecuteCron(schedule, now);
        }

        // Legacy: Use frequency + hour for backward compatibility (Phase 3 and earlier)
        return shouldExecuteLegacy(schedule, now);
    }

    /**
     * Phase 4: Cron-based schedule evaluation
     */
    private boolean shouldExecuteCron(ReportSchedule schedule, LocalDateTime now) {
        try {
            CronExpression cron = new CronExpression(schedule.getCronExpression());

            // Determine the base time for calculating next execution
            LocalDateTime baseTime = schedule.getLastExecuted() != null
                ? schedule.getLastExecuted()
                : now.minusHours(1); // If never executed, check from 1 hour ago

            Date baseDate = Date.from(baseTime.atZone(ZoneId.systemDefault()).toInstant());
            Date nextExecution = cron.getNextValidTimeAfter(baseDate);

            if (nextExecution == null) {
                return false;
            }

            LocalDateTime nextExecutionTime = LocalDateTime.ofInstant(
                nextExecution.toInstant(),
                ZoneId.systemDefault()
            );

            // Execute if the next scheduled time is in the past or current hour
            // This ensures we catch schedules that should have run
            boolean shouldExecute = !nextExecutionTime.isAfter(now);

            if (shouldExecute) {
                log.info("✅ Cron schedule {} matched: next execution was {}",
                    schedule.getCronExpression(), nextExecutionTime);
            }

            return shouldExecute;
        } catch (ParseException e) {
            log.error("❌ Invalid cron expression for schedule {}: {}",
                schedule.getId(), schedule.getCronExpression(), e);
            return false;
        }
    }

    /**
     * Phase 3 and earlier: Legacy frequency-based schedule evaluation
     */
    private boolean shouldExecuteLegacy(ReportSchedule schedule, LocalDateTime now) {
        // Check if it's the execution time (default to 9 AM if not specified)
        LocalTime currentTime = now.toLocalTime();
        int executionHour = schedule.getHour() != null ? schedule.getHour() : 9;
        if (currentTime.getHour() != executionHour) {
            return false;
        }

        LocalDateTime lastExecuted = schedule.getLastExecuted();

        switch (schedule.getFrequency()) {
            case "daily":
                // Execute if not run today
                return lastExecuted == null || lastExecuted.toLocalDate().isBefore(now.toLocalDate());

            case "weekly":
                // Execute on Monday if not run this week
                if (now.getDayOfWeek() != DayOfWeek.MONDAY) {
                    return false;
                }
                return lastExecuted == null || lastExecuted.toLocalDate().isBefore(now.toLocalDate());

            case "monthly":
                // Execute on 1st of month if not run this month
                if (now.getDayOfMonth() != 1) {
                    return false;
                }
                return lastExecuted == null || lastExecuted.toLocalDate().isBefore(now.toLocalDate());

            default:
                return false;
        }
    }

    private void executeSchedule(ReportSchedule schedule) throws Exception {
        log.info("📊 Executing scheduled report for: {}", schedule.getEmail());

        Map<String, Object> filters = schedule.getFilters();

        // Get userId from schedule object, not from filters (filters.userId might be Integer from frontend)
        String userId = schedule.getUserId();

        // Parse filters
        String startDateStr = (String) filters.get("startDate");
        String endDateStr = (String) filters.get("endDate");
        String exportFormat = filters.getOrDefault("exportFormat", "excel").toString();
        List<String> selectedColumns = (List<String>) filters.get("selectedColumns");
        String sortOrder = filters.getOrDefault("sortOrder", "dateDesc").toString();
        Boolean includeSummarySheet = (Boolean) filters.getOrDefault("includeSummarySheet", true);

        // Calculate date range: Use saved filters if available, otherwise rolling 30 days
        LocalDateTime startDate;
        LocalDateTime endDate;

        if (startDateStr != null && !startDateStr.trim().isEmpty() &&
            endDateStr != null && !endDateStr.trim().isEmpty()) {
            // Use saved date filters from schedule
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
                LocalDate startLocalDate = LocalDate.parse(startDateStr, formatter);
                LocalDate endLocalDate = LocalDate.parse(endDateStr, formatter);
                startDate = startLocalDate.atStartOfDay();
                endDate = endLocalDate.atTime(23, 59, 59);
                log.info("📅 Using saved date filters: {} to {}", startDate, endDate);
            } catch (Exception e) {
                log.warn("⚠️ Failed to parse saved dates, using rolling 30 days instead: {}", e.getMessage());
                endDate = LocalDateTime.now();
                startDate = endDate.minusDays(30);
            }
        } else {
            // Rolling period: last 30 days (default for Phase 3 schedules without date filters)
            endDate = LocalDateTime.now();
            startDate = endDate.minusDays(30);
            log.info("📅 Using rolling date range (last 30 days): {} to {}", startDate, endDate);
        }

        // Fetch transactions
        List<Transaction> transactions = transactionService.exportAll(
                userId, null, null, startDate, endDate,
                null, null, null, null, null);

        // Generate report
        byte[] reportBytes;
        String fileName;
        String formatLabel;

        switch (exportFormat.toLowerCase()) {
            case "pdf":
                reportBytes = pdfExportService.generatePdfFile(transactions, sortOrder, includeSummarySheet, selectedColumns);
                fileName = "scheduled_report.pdf";
                formatLabel = "PDF";
                break;
            case "csv":
                reportBytes = csvExportService.generateCsvFile(transactions, sortOrder, selectedColumns);
                fileName = "scheduled_report.csv";
                formatLabel = "CSV";
                break;
            case "excel":
            default:
                reportBytes = excelExportService.generateExcelFile(transactions, sortOrder, includeSummarySheet, selectedColumns);
                fileName = "scheduled_report.xlsx";
                formatLabel = "Excel";
                break;
        }

        // Send email
        emailService.sendReportEmail(schedule.getEmail(), reportBytes, fileName, formatLabel);
        log.info("✅ Scheduled report sent to: {}", schedule.getEmail());

        // Calculate total spent for SMS summary and history
        double totalSpent = transactions.stream()
            .filter(t -> t.getAmountOut() != null && t.getAmountOut() > 0)
            .mapToDouble(t -> t.getAmountOut())
            .sum();

        // Determine report type for display
        String reportType = schedule.getCronExpression() != null ? "custom" :
                           (schedule.getFrequency() != null ? schedule.getFrequency() : "scheduled");

        // Phase 5: Send web notification on success
        boolean notificationSent = false;
        if (schedule.getWebNotificationEnabled() != null && schedule.getWebNotificationEnabled()
            && schedule.getNotifyOnSuccess() != null && schedule.getNotifyOnSuccess()) {
            try {
                Long userIdLong = Long.parseLong(userId);
                notificationSent = reportNotificationService.sendReportReadyNotification(
                    userIdLong,
                    schedule.getEmail(),
                    reportType,
                    transactions.size(),
                    totalSpent
                );
            } catch (NumberFormatException nfe) {
                log.error("❌ Invalid userId format: {}", userId);
            }
        }

        // Phase 5: Save report history
        saveReportHistory(schedule, exportFormat, transactions.size(), totalSpent, reportType, true, null, true, notificationSent);
    }

    /**
     * Save report execution history to database
     * Phase 5: Report History tracking
     */
    private void saveReportHistory(ReportSchedule schedule, String exportFormat, int transactionCount,
                                   double totalAmount, String reportType, boolean success, String errorMessage,
                                   boolean emailSent, boolean notificationSent) {
        try {
            ReportHistory history = ReportHistory.builder()
                .userId(schedule.getUserId())
                .scheduleId(schedule.getId())
                .email(schedule.getEmail())
                .reportType(reportType)
                .exportFormat(exportFormat)
                .transactionCount(transactionCount)
                .totalAmount(totalAmount)
                .executedAt(LocalDateTime.now())
                .status(success ? "success" : "failed")
                .errorMessage(errorMessage)
                .emailSent(emailSent)
                .notificationSent(notificationSent)
                .filters(schedule.getFilters())
                .createdAt(LocalDateTime.now())
                .build();

            historyRepository.save(history);
            log.info("✅ Report history saved for schedule: {}", schedule.getId());
        } catch (Exception e) {
            log.error("❌ Failed to save report history: {}", e.getMessage(), e);
            // Don't throw exception - history saving failure should not affect report execution
        }
    }
}
