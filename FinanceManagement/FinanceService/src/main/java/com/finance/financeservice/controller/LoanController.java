package com.finance.financeservice.controller;

import com.finance.financeservice.mongo.document.Loan;
import com.finance.financeservice.service.LoanService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
public class LoanController {

    private final LoanService loanService;

    @GetMapping("/{id}")
    public ResponseEntity<Loan> getById(@PathVariable String id) {
        return loanService.getById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Loan> create(@RequestBody Loan loan) {
        return ResponseEntity.ok(loanService.create(loan));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Loan> update(@PathVariable String id, @RequestBody Loan loan) {
        try {
            return ResponseEntity.ok(loanService.update(id, loan));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        loanService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<Page<Loan>> filter(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "loanType", required = false) String loanTypeStr,
            @RequestParam(value = "searchTerm", required = false) String searchTerm,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            @RequestParam(value = "statuses", required = false) String statusesStr,
            @RequestParam(value = "reconciliationDate", required = false) String reconciliationDateStr,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        
        Loan.LoanType loanType = null;
        if (loanTypeStr != null && !loanTypeStr.isEmpty()) {
            try {
                loanType = Loan.LoanType.valueOf(loanTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid loan type, will be ignored
            }
        }
        
        LocalDate startDate = parseDate(startDateStr);
        LocalDate endDate = parseDate(endDateStr);
        LocalDate reconciliationDate = parseDate(reconciliationDateStr);
        
        List<Loan.LoanStatus> statuses = null;
        if (statusesStr != null && !statusesStr.isEmpty()) {
            try {
                statuses = Arrays.stream(statusesStr.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .map(Loan.LoanStatus::valueOf)
                    .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                // Invalid status, will be ignored
            }
        }
        
        return ResponseEntity.ok(loanService.filter(userId, loanType, searchTerm,
                startDate, endDate, statuses, reconciliationDate, page, size));
    }

    @GetMapping("/summary")
    public ResponseEntity<LoanService.LoanSummary> summary(
            @RequestParam(value = "userId", required = false) String userId,
            @RequestParam(value = "loanType", required = false) String loanTypeStr) {
        
        Loan.LoanType loanType = null;
        if (loanTypeStr != null && !loanTypeStr.isEmpty()) {
            try {
                loanType = Loan.LoanType.valueOf(loanTypeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Invalid loan type, will be ignored
            }
        }
        
        return ResponseEntity.ok(loanService.summary(userId, loanType));
    }

    @PostMapping("/{loanId}/payments")
    public ResponseEntity<Loan> addPayment(
            @PathVariable String loanId,
            @RequestBody Loan.LoanPayment payment) {
        try {
            return ResponseEntity.ok(loanService.addPayment(loanId, payment));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{loanId}/payments/{paymentId}")
    public ResponseEntity<Loan> updatePayment(
            @PathVariable String loanId,
            @PathVariable String paymentId,
            @RequestBody Loan.LoanPayment payment) {
        try {
            return ResponseEntity.ok(loanService.updatePayment(loanId, paymentId, payment));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{loanId}/payments/{paymentId}")
    public ResponseEntity<Loan> deletePayment(
            @PathVariable String loanId,
            @PathVariable String paymentId) {
        try {
            return ResponseEntity.ok(loanService.deletePayment(loanId, paymentId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
        } catch (Exception e) {
            // Try alternative formats
            DateTimeFormatter[] formatters = {
                DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                DateTimeFormatter.ofPattern("dd-MM-yyyy")
            };
            for (DateTimeFormatter formatter : formatters) {
                try {
                    return LocalDate.parse(dateStr, formatter);
                } catch (Exception ignored) {
                    // Try next format
                }
            }
            return null;
        }
    }
}

