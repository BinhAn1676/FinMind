package com.finance.financeservice.controller;

import com.finance.financeservice.dto.cron.CronPresetDto;
import com.finance.financeservice.dto.cron.CronValidationResponse;
import com.finance.financeservice.dto.cron.NextExecutionsResponse;
import com.finance.financeservice.service.CronExpressionValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/cron")
@RequiredArgsConstructor
@Slf4j
public class CronExpressionController {

    private final CronExpressionValidator cronValidator;
    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Validate a cron expression
     * @param expression The cron expression to validate
     * @return Validation response with valid flag, message, and description
     */
    @GetMapping("/validate")
    public ResponseEntity<CronValidationResponse> validateCron(@RequestParam String expression) {
        log.info("📋 Validating cron expression: {}", expression);
        CronValidationResponse response = cronValidator.validate(expression);
        log.info("✅ Validation result: {}", response.isValid() ? "VALID" : "INVALID");
        return ResponseEntity.ok(response);
    }

    /**
     * Get human-readable description of a cron expression
     * @param expression The cron expression
     * @return Validation response with description
     */
    @GetMapping("/describe")
    public ResponseEntity<CronValidationResponse> describeCron(@RequestParam String expression) {
        log.info("📝 Generating description for cron: {}", expression);
        CronValidationResponse response = cronValidator.validate(expression);
        return ResponseEntity.ok(response);
    }

    /**
     * Get list of common cron presets for financial reports
     * @return List of preset cron expressions
     */
    @GetMapping("/presets")
    public ResponseEntity<List<CronPresetDto>> getCronPresets() {
        log.info("📚 Fetching cron presets");
        List<CronPresetDto> presets = cronValidator.getPresets();
        log.info("✅ Returning {} presets", presets.size());
        return ResponseEntity.ok(presets);
    }

    /**
     * Calculate next N execution times for a cron expression
     * @param expression The cron expression
     * @param count Number of next executions to calculate (default: 5)
     * @return List of next execution times in ISO-8601 format
     */
    @GetMapping("/next-executions")
    public ResponseEntity<NextExecutionsResponse> getNextExecutions(
            @RequestParam String expression,
            @RequestParam(defaultValue = "5") int count
    ) {
        log.info("🕐 Calculating next {} executions for cron: {}", count, expression);

        // Validate expression first
        CronValidationResponse validation = cronValidator.validate(expression);
        if (!validation.isValid()) {
            log.warn("❌ Invalid cron expression: {}", validation.getMessage());
            return ResponseEntity.ok(NextExecutionsResponse.builder()
                    .nextExecutions(List.of())
                    .build());
        }

        List<LocalDateTime> executions = cronValidator.getNextExecutions(expression, count);
        List<String> executionStrings = executions.stream()
                .map(ISO_FORMATTER::format)
                .collect(Collectors.toList());

        log.info("✅ Calculated {} next executions", executionStrings.size());
        return ResponseEntity.ok(NextExecutionsResponse.builder()
                .nextExecutions(executionStrings)
                .build());
    }
}
