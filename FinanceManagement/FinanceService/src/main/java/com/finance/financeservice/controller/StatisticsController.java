package com.finance.financeservice.controller;

import com.finance.financeservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final TransactionService transactionService;

    @GetMapping("/dashboard/summary")
    public ResponseEntity<TransactionService.TransactionDashboardSummary> dashboardSummary(
            @RequestParam(value = "userId") String userId,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr) {
        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);
        return ResponseEntity.ok(transactionService.dashboardSummary(userId, startDate, endDate));
    }

    @GetMapping("/breakdown")
    public ResponseEntity<TransactionService.CategoryBreakdown> breakdown(
            @RequestParam("userId") String userId,
            @RequestParam("type") String type, // expense | income
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr) {
        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);
        return ResponseEntity.ok(transactionService.getCategoryBreakdown(userId, type, startDate, endDate));
    }

    @GetMapping("/breakdown/by-accounts")
    public ResponseEntity<TransactionService.CategoryBreakdown> breakdownByBankAccounts(
            @RequestParam("bankAccountIds") List<String> bankAccountIds,
            @RequestParam("type") String type, // expense | income
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr) {
        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);
        return ResponseEntity.ok(transactionService.getCategoryBreakdownByBankAccountIds(bankAccountIds, type, startDate, endDate));
    }
    
    @GetMapping("/top-biggest")
    public ResponseEntity<List<com.finance.financeservice.mongo.document.Transaction>> topBiggestTransactions(
            @RequestParam("userId") String userId,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            @RequestParam(value = "limit", defaultValue = "6") int limit) {
        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);
        return ResponseEntity.ok(transactionService.getTopBiggestTransactions(userId, startDate, endDate, limit));
    }
    
    @GetMapping("/variance")
    public ResponseEntity<TransactionService.CategoryVariance> categoryVariance(
            @RequestParam("userId") String userId,
            @RequestParam("type") String type, // expense | income
            @RequestParam(value = "currentStartDate", required = false) String currentStartDateStr,
            @RequestParam(value = "currentEndDate", required = false) String currentEndDateStr,
            @RequestParam(value = "previousStartDate", required = false) String previousStartDateStr,
            @RequestParam(value = "previousEndDate", required = false) String previousEndDateStr) {
        LocalDateTime currentStart = parseStartDate(currentStartDateStr);
        LocalDateTime currentEnd = parseEndDate(currentEndDateStr);
        LocalDateTime previousStart = parseStartDate(previousStartDateStr);
        LocalDateTime previousEnd = parseEndDate(previousEndDateStr);
        return ResponseEntity.ok(transactionService.getCategoryVariance(userId, type, currentStart, currentEnd, previousStart, previousEnd));
    }
    
    @GetMapping("/expense-heatmap")
    public ResponseEntity<TransactionService.ExpenseHeatmap> expenseHeatmap(
            @RequestParam("userId") String userId,
            @RequestParam("year") int year,
            @RequestParam("month") int month,
            @RequestParam(value = "dailyLimit", required = false) Double manualDailyLimit) {
        return ResponseEntity.ok(transactionService.getDailyExpenseHeatmap(userId, year, month, manualDailyLimit));
    }

    /**
     * Parse start date, normalizing to start of day (00:00:00) when only a date is provided.
     */
    private LocalDateTime parseStartDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            if (dateStr.contains("T")) {
                return LocalDateTime.parse(dateStr);
            }
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
            return date.atStartOfDay();
        } catch (Exception e) {
            DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                    DateTimeFormatter.ofPattern("dd-MM-yyyy")
            };
            for (DateTimeFormatter f : formatters) {
                try {
                    LocalDate d = LocalDate.parse(dateStr, f);
                    return d.atStartOfDay();
                } catch (Exception ignored) { }
            }
            return null;
        }
    }

    /**
     * Parse end date, normalizing to end of day (23:59:59) when only a date is provided.
     */
    private LocalDateTime parseEndDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            if (dateStr.contains("T")) {
                return LocalDateTime.parse(dateStr);
            }
            LocalDate date = LocalDate.parse(dateStr, DateTimeFormatter.ISO_DATE);
            return date.atTime(23, 59, 59);
        } catch (Exception e) {
            DateTimeFormatter[] formatters = {
                    DateTimeFormatter.ofPattern("yyyy-MM-dd"),
                    DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                    DateTimeFormatter.ofPattern("MM/dd/yyyy"),
                    DateTimeFormatter.ofPattern("dd-MM-yyyy")
            };
            for (DateTimeFormatter f : formatters) {
                try {
                    LocalDate d = LocalDate.parse(dateStr, f);
                    return d.atTime(23, 59, 59);
                } catch (Exception ignored) { }
            }
            return null;
        }
    }
}

