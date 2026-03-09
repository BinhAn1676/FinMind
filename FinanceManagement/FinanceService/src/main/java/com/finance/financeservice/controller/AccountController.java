package com.finance.financeservice.controller;

import com.finance.financeservice.mysql.entity.Account;
import com.finance.financeservice.service.AccountService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/{id}")
    public ResponseEntity<Account> getById(@PathVariable Long id) {
        return accountService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/batch")
    public ResponseEntity<java.util.List<Account>> getByIds(@RequestBody java.util.List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.ok(java.util.Collections.emptyList());
        }
        return ResponseEntity.ok(accountService.getByIds(ids));
    }

    @GetMapping
    public ResponseEntity<Page<Account>> filter(@RequestParam(value = "userId", required = false) String userId,
                                                @RequestParam(value = "textSearch", required = false) String textSearch,
                                                @RequestParam(value = "page", defaultValue = "0") int page,
                                                @RequestParam(value = "size", defaultValue = "5") int size) {
        return ResponseEntity.ok(accountService.filter(userId, textSearch, page, size));
    }

    @PostMapping
    public ResponseEntity<Account> create(@RequestParam("userId") String userId, @RequestBody CreateAccountRequest request) {
        return ResponseEntity.ok(accountService.createAccount(
                userId,
                request.getAccountNumber(),
                request.getAccountHolderName(),
                request.getBankShortName(),
                request.getBankFullName(),
                request.getBankCode(),
                request.getLabel(),
                request.getAccumulated()
        ));
    }

    @PostMapping("/{id}")
    public ResponseEntity<Account> update(@PathVariable Long id, @RequestBody UpdateAccountRequest request) {
        return ResponseEntity.ok(accountService.updateAccount(id, request.getLabel(), request.getAccumulated()));
    }

    @GetMapping("/summary")
    public ResponseEntity<AccountService.AccountSummary> summary(@RequestParam(value = "userId", required = false) String userId) {
        return ResponseEntity.ok(accountService.summary(userId));
    }

    @Data
    public static class CreateAccountRequest {
        private String accountNumber;
        private String accountHolderName;
        private String bankShortName;
        private String bankFullName;
        private String bankCode;
        private String label;
        private String accumulated;
    }

    @Data
    public static class UpdateAccountRequest {
        private String label;
        private String accumulated; // optional, ignored for auto-sync banks
    }

    @GetMapping("/sync-support")
    public ResponseEntity<AccountService.SyncSupport> syncSupport() {
        return ResponseEntity.ok(accountService.syncSupport());
    }

    @GetMapping("/distribution")
    public ResponseEntity<java.util.List<AccountService.AccountDistribution>> getAccountDistribution(@RequestParam(value = "userId", required = false) String userId) {
        return ResponseEntity.ok(accountService.getAccountDistribution(userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteAccount(@PathVariable Long id) {
        try {
            accountService.deleteAccount(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}


