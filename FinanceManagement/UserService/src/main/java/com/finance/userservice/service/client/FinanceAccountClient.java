package com.finance.userservice.service.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "finances", path = "/api/v1/accounts")
public interface FinanceAccountClient {

    @GetMapping("/{id}")
    AccountResponse getAccountById(@PathVariable("id") Long accountId);

    @PostMapping("/batch")
    List<AccountResponse> getAccountsByIds(@RequestBody List<Long> accountIds);

    @Data
    class AccountResponse {
        private Long id;
        private String userId;
        private String bankAccountId;
        private String label;
        private String bankFullName;
        private String bankShortName;
        private String accountNumber;
        private String accumulated;
        private String bankCode;
        private String active;
        private String currency;
    }
}





