package com.finance.financeservice.dto.keys;

import lombok.Data;

@Data
public class DecryptResponse {
    private boolean success;
    private String message;
    private String decryptedData;
}


