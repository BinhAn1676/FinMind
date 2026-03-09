package com.finance.userservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyGenerateResponse {
    private String userId;
    private String keyVersion;
    private String message;
    private boolean success;
}


