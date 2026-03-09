package com.finance.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyDecryptResponse {
    private String userId;
    private String decryptedData;
    private String message;
    private boolean success;
}


