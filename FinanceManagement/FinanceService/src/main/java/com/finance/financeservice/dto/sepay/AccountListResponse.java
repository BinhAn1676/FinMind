package com.finance.financeservice.dto.sepay;

import lombok.Data;

import java.util.List;

@Data
public class AccountListResponse {
    private int status;
    private Object error;
    private Messages messages;
    private List<BankAccountDto> bankaccounts;

    @Data
    public static class Messages {
        private boolean success;
    }
}


