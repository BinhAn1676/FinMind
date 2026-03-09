package com.finance.financeservice.dto.sepay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResult {
    private boolean success;
    private String message;
    private int processedCount;
}


