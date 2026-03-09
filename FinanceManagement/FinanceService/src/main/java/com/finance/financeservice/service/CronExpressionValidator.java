package com.finance.financeservice.service;

import com.finance.financeservice.dto.cron.CronPresetDto;
import com.finance.financeservice.dto.cron.CronValidationResponse;

import java.time.LocalDateTime;
import java.util.List;

public interface CronExpressionValidator {

    /**
     * Validate a cron expression
     * @param expression The cron expression to validate
     * @return Validation response with valid flag, message, and description
     */
    CronValidationResponse validate(String expression);

    /**
     * Generate human-readable description of a cron expression
     * @param expression The cron expression
     * @return Human-readable description
     */
    String generateDescription(String expression);

    /**
     * Get list of common cron presets for financial reports
     * @return List of preset cron expressions
     */
    List<CronPresetDto> getPresets();

    /**
     * Calculate next N execution times for a cron expression
     * @param expression The cron expression
     * @param count Number of next executions to calculate
     * @return List of next execution times
     */
    List<LocalDateTime> getNextExecutions(String expression, int count);
}
