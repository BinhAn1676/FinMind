package com.finance.financeservice.dto.keys;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptResponse {
    private boolean success;
    private String encryptedData;
    private String message;
}


