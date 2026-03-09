package com.finance.financeservice.dto.cron;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NextExecutionsResponse {
    private List<String> nextExecutions; // ISO-8601 formatted timestamps
}
