package com.finance.financeservice.dto.cron;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CronValidationResponse {
    private boolean valid;
    private String message;
    private String description; // Human-readable description of the cron expression
}
