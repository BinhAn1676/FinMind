package com.finance.financeservice.service.impl;

import com.finance.financeservice.dto.cron.CronPresetDto;
import com.finance.financeservice.dto.cron.CronValidationResponse;
import com.finance.financeservice.service.CronExpressionValidator;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronExpression;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@Slf4j
public class CronExpressionValidatorImpl implements CronExpressionValidator {

    @Override
    public CronValidationResponse validate(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return CronValidationResponse.builder()
                    .valid(false)
                    .message("Cron expression cannot be empty")
                    .description("")
                    .build();
        }

        try {
            new CronExpression(expression.trim());
            String description = generateDescription(expression.trim());
            return CronValidationResponse.builder()
                    .valid(true)
                    .message("Valid cron expression")
                    .description(description)
                    .build();
        } catch (ParseException e) {
            return CronValidationResponse.builder()
                    .valid(false)
                    .message("Invalid cron expression: " + e.getMessage())
                    .description("")
                    .build();
        }
    }

    @Override
    public String generateDescription(String expression) {
        try {
            CronExpression cron = new CronExpression(expression);
            // Parse the cron expression to generate a human-readable description
            String[] parts = expression.trim().split("\\s+");

            if (parts.length < 6) {
                return "Custom schedule";
            }

            String minute = parts[1];
            String hour = parts[2];
            String dayOfMonth = parts[3];
            String month = parts[4];
            String dayOfWeek = parts[5];

            StringBuilder description = new StringBuilder();

            // Check for high-frequency schedules first (every minute, every hour)
            if ("*".equals(minute) && "*".equals(hour)) {
                description.append("Every minute");
                return description.toString();
            } else if ("*".equals(minute)) {
                description.append("Every minute");
                return description.toString();
            } else if ("*".equals(hour) && "0".equals(minute)) {
                description.append("Every hour");
                return description.toString();
            }

            // Frequency
            if ("*".equals(dayOfMonth) && "*".equals(month) && "?".equals(dayOfWeek)) {
                description.append("Every day");
            } else if ("?".equals(dayOfMonth) && dayOfWeek.contains("MON-FRI")) {
                description.append("Every weekday");
            } else if ("?".equals(dayOfMonth) && dayOfWeek.contains(",")) {
                description.append("Every ").append(dayOfWeek.replace("?", ""));
            } else if ("?".equals(dayOfMonth) && !"*".equals(dayOfWeek) && !"?".equals(dayOfWeek)) {
                description.append("Every ").append(dayOfWeek);
            } else if ("1".equals(dayOfMonth)) {
                description.append("First day of month");
            } else if ("L".equals(dayOfMonth)) {
                description.append("Last day of month");
            } else if (dayOfMonth.contains(",")) {
                description.append("Day ").append(dayOfMonth).append(" of month");
            } else if (!"*".equals(dayOfMonth) && !"?".equals(dayOfMonth)) {
                description.append("Day ").append(dayOfMonth).append(" of month");
            } else {
                description.append("Custom schedule");
            }

            // Time
            if (hour.contains("/")) {
                description.append(" every ").append(hour.substring(hour.indexOf("/") + 1)).append(" hours");
            } else if (!"*".equals(hour)) {
                description.append(" at ").append(String.format("%02d", Integer.parseInt(hour)));
                if (!"0".equals(minute)) {
                    description.append(":").append(String.format("%02d", Integer.parseInt(minute)));
                } else {
                    description.append(":00");
                }
            }

            return description.toString();
        } catch (Exception e) {
            log.warn("Failed to generate description for cron: {}", expression, e);
            return "Custom schedule";
        }
    }

    @Override
    public List<CronPresetDto> getPresets() {
        return List.of(
                CronPresetDto.builder()
                        .name("Every day at 9 AM")
                        .expression("0 0 9 * * ?")
                        .description("Runs daily at 9:00 AM")
                        .build(),
                CronPresetDto.builder()
                        .name("Every Monday at 9 AM")
                        .expression("0 0 9 ? * MON")
                        .description("Runs every Monday at 9:00 AM")
                        .build(),
                CronPresetDto.builder()
                        .name("Every weekday at 9 AM")
                        .expression("0 0 9 ? * MON-FRI")
                        .description("Runs Monday to Friday at 9:00 AM")
                        .build(),
                CronPresetDto.builder()
                        .name("First day of month at 9 AM")
                        .expression("0 0 9 1 * ?")
                        .description("Runs on the 1st day of every month at 9:00 AM")
                        .build(),
                CronPresetDto.builder()
                        .name("1st and 15th of month at 9 AM")
                        .expression("0 0 9 1,15 * ?")
                        .description("Runs on the 1st and 15th of every month at 9:00 AM")
                        .build(),
                CronPresetDto.builder()
                        .name("Last day of month at 9 AM")
                        .expression("0 0 9 L * ?")
                        .description("Runs on the last day of every month at 9:00 AM")
                        .build(),
                CronPresetDto.builder()
                        .name("Every 6 hours")
                        .expression("0 0 */6 * * ?")
                        .description("Runs every 6 hours (00:00, 06:00, 12:00, 18:00)")
                        .build(),
                CronPresetDto.builder()
                        .name("Every 4 hours")
                        .expression("0 0 */4 * * ?")
                        .description("Runs every 4 hours")
                        .build()
        );
    }

    @Override
    public List<LocalDateTime> getNextExecutions(String expression, int count) {
        List<LocalDateTime> executions = new ArrayList<>();

        try {
            CronExpression cron = new CronExpression(expression);
            Date current = new Date();

            for (int i = 0; i < count; i++) {
                Date nextExecution = cron.getNextValidTimeAfter(current);
                if (nextExecution == null) {
                    break;
                }

                LocalDateTime nextExecutionTime = LocalDateTime.ofInstant(
                        nextExecution.toInstant(),
                        ZoneId.systemDefault()
                );
                executions.add(nextExecutionTime);
                current = nextExecution;
            }
        } catch (ParseException e) {
            log.error("Failed to calculate next executions for cron: {}", expression, e);
        }

        return executions;
    }
}
