package com.finance.financeservice.scheduler;

import com.finance.financeservice.common.config.KafkaConfig;
import com.finance.financeservice.dto.user.UserDto;
import com.finance.financeservice.event.UserNotificationEvent;
import com.finance.financeservice.mongo.document.BillReminder;
import com.finance.financeservice.service.BillReminderService;
import com.finance.financeservice.service.client.UserServiceClient;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class BillReminderJob implements Job {

    @Autowired
    private BillReminderService billReminderService;

    @Autowired
    private KafkaTemplate<String, UserNotificationEvent> notificationKafkaTemplate;

    @Autowired
    private UserServiceClient userServiceClient;

    @Override
    public void execute(JobExecutionContext context) {
        LocalDate today = LocalDate.now();
        log.info("Running BillReminderJob for date: {}", today);

        List<BillReminder> activeBills = billReminderService.findAllActive();
        log.info("Found {} active bill reminders", activeBills.size());

        String currentPeriod = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));

        for (BillReminder bill : activeBills) {
            try {
                // Skip if already paid this period
                boolean alreadyPaid = bill.getPayments().stream()
                        .anyMatch(p -> currentPeriod.equals(p.getPeriod()));
                if (alreadyPaid) {
                    continue;
                }

                // Calculate days until due
                int daysUntilDue = calculateDaysUntilDue(bill, today);
                int remindBefore = bill.getRemindDaysBefore() != null ? bill.getRemindDaysBefore() : 3;

                if (daysUntilDue >= 0 && daysUntilDue <= remindBefore) {
                    sendBillReminderNotification(bill, daysUntilDue);
                }
            } catch (Exception e) {
                log.error("Failed to process bill reminder for bill {}: {}", bill.getId(), e.getMessage());
            }
        }
    }

    private int calculateDaysUntilDue(BillReminder bill, LocalDate today) {
        int dayOfMonth = bill.getDayOfMonth() != null ? bill.getDayOfMonth() : 1;
        LocalDate dueDate = today.withDayOfMonth(Math.min(dayOfMonth, today.lengthOfMonth()));
        if (dueDate.isBefore(today)) {
            // Move to next month
            LocalDate nextMonth = today.plusMonths(1);
            dueDate = nextMonth.withDayOfMonth(Math.min(dayOfMonth, nextMonth.lengthOfMonth()));
        }
        return (int) (dueDate.toEpochDay() - today.toEpochDay());
    }

    private void sendBillReminderNotification(BillReminder bill, int daysLeft) {
        try {
            Long userId = Long.parseLong(bill.getUserId());
            String daysText = daysLeft == 0 ? "hôm nay" : "còn " + daysLeft + " ngày";
            String amountFormatted = String.format("%,.0f", bill.getAmount() != null ? bill.getAmount() : 0);
            String message = String.format("%s %sđ đến hạn ngày %d (%s)",
                    bill.getName(),
                    amountFormatted,
                    bill.getDayOfMonth(),
                    daysText);

            // Fetch user email from UserService
            String userEmail = null;
            String userFullName = null;
            try {
                UserDto user = userServiceClient.getById(userId);
                userEmail = user.getEmail();
                userFullName = user.getFullName();
            } catch (Exception e) {
                log.warn("Could not fetch user info for userId {}: {}", userId, e.getMessage());
            }

            UserNotificationEvent event = UserNotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType("BILL_REMINDER")
                    .userId(userId)
                    .email(userEmail)
                    .fullName(userFullName)
                    .source("FINANCE_SERVICE")
                    .title("⏰ Nhắc nhở hóa đơn")
                    .message(message)
                    .timestamp(LocalDateTime.now())
                    .build();

            notificationKafkaTemplate.send(KafkaConfig.USER_NOTIFICATION_TOPIC, bill.getUserId(), event);
            log.info("Sent bill reminder notification for bill '{}' to user {} (email: {})",
                    bill.getName(), bill.getUserId(), userEmail != null ? userEmail : "N/A");
        } catch (NumberFormatException e) {
            log.error("Invalid userId format for bill {}: {}", bill.getId(), bill.getUserId());
        } catch (Exception e) {
            log.error("Failed to send Kafka notification for bill {}: {}", bill.getId(), e.getMessage());
        }
    }
}
