package com.finance.financeservice.controller;

import com.finance.financeservice.mysql.entity.ExternalAccount;
import com.finance.financeservice.service.ExternalAccountService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/external-accounts")
@RequiredArgsConstructor
public class ExternalAccountController {

    private final ExternalAccountService externalAccountService;

    @GetMapping("/{id}")
    public ResponseEntity<ExternalAccount> getById(@PathVariable Long id) {
        return externalAccountService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<Page<ExternalAccount>> filter(@RequestParam(value = "userId", required = false) String userId,
                                                        @RequestParam(value = "textSearch", required = false) String textSearch,
                                                        @RequestParam(value = "page", defaultValue = "0") int page,
                                                        @RequestParam(value = "size", defaultValue = "5") int size) {
        return ResponseEntity.ok(externalAccountService.filter(userId, textSearch, page, size));
    }

    @PostMapping
    public ResponseEntity<ExternalAccount> create(@RequestBody CreateExternalAccountRequest request,
                                                  @RequestParam(value = "userId", required = false) String userId) {
        return ResponseEntity.ok(externalAccountService.createExternalAccount(
                userId,
                request.getLabel(),
                request.getType(),
                request.getAccumulated(),
                request.getDescription()
        ));
    }

    @PostMapping("/{id}")
    public ResponseEntity<ExternalAccount> update(@PathVariable Long id, @RequestBody UpdateExternalAccountRequest request) {
        return ResponseEntity.ok(externalAccountService.updateExternalAccount(
                id,
                request.getLabel(),
                request.getType(),
                request.getAccumulated(),
                request.getDescription()
        ));
    }

    @GetMapping("/summary")
    public ResponseEntity<ExternalAccountService.ExternalAccountSummary> summary(@RequestParam(value = "userId", required = false) String userId) {
        return ResponseEntity.ok(externalAccountService.summary(userId));
    }

    @GetMapping("/distribution")
    public ResponseEntity<java.util.List<ExternalAccountService.ExternalAccountDistribution>> getExternalAccountDistribution(@RequestParam(value = "userId", required = false) String userId) {
        return ResponseEntity.ok(externalAccountService.getExternalAccountDistribution(userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteExternalAccount(@PathVariable Long id) {
        try {
            externalAccountService.deleteExternalAccount(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @Data
    public static class CreateExternalAccountRequest {
        private String label;
        private String type;
        private String accumulated;
        private String description;
    }

    @Data
    public static class UpdateExternalAccountRequest {
        private String label;
        private String type;
        private String accumulated;
        private String description;
    }
}

