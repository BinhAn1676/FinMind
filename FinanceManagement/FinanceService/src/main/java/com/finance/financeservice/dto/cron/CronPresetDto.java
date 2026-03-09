package com.finance.financeservice.dto.cron;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CronPresetDto {
    private String name;
    private String expression;
    private String description;
}
