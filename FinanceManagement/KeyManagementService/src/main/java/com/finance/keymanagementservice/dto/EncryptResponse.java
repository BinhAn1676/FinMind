package com.finance.keymanagementservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EncryptResponse {
    
    private String userId;
    private String encryptedData;
    private String message;
    private boolean success;
}

