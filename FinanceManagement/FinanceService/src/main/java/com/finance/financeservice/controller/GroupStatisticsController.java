package com.finance.financeservice.controller;

import com.finance.financeservice.mongo.document.Transaction;
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

/**
 * Controller for group-level statistics.
 * All endpoints work with bank account IDs that belong to a group.
 * The groupId is for reference only; the actual data is filtered by bankAccountIds.
 */
@RestController
@RequestMapping("/api/v1/statistics/group")
@RequiredArgsConstructor
public class GroupStatisticsController {

    private final TransactionService transactionService;

    /**
     * Get dashboard summary for a group (4 summary boxes)
     * Similar to user-level dashboard but aggregated across all group bank accounts
     */
    @GetMapping("/dashboard/summary")
    public ResponseEntity<TransactionService.TransactionDashboardSummary> dashboardSummary(
            @RequestParam("bankAccountIds") List<String> bankAccountIds,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr) {
        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);
        return ResponseEntity.ok(transactionService.dashboardSummaryByBankAccountIds(bankAccountIds, startDate, endDate));
    }

    /**
     * Get monthly cashflow for a group
     */
    @GetMapping("/cashflow")
    public ResponseEntity<List<TransactionService.CashflowPoint>> cashflow(
            @RequestParam("bankAccountIds") List<String> bankAccountIds,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr) {
        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);
        return ResponseEntity.ok(transactionService.getCashflowByBankAccountIds(bankAccountIds, startDate, endDate));
    }

    /**
     * Get category breakdown for a group (expense or income)
     * Reuses existing endpoint: /statistics/breakdown/by-accounts
     * This is just an alias for consistency
     */
    @GetMapping("/breakdown")
    public ResponseEntity<TransactionService.CategoryBreakdown> breakdown(
            @RequestParam("bankAccountIds") List<String> bankAccountIds,
            @RequestParam("type") String type, // expense | income
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr) {
        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);
        return ResponseEntity.ok(transactionService.getCategoryBreakdownByBankAccountIds(bankAccountIds, type, startDate, endDate));
    }

    /**
     * Get top biggest expense transactions for a group
     */
    @GetMapping("/top-biggest")
    public ResponseEntity<List<Transaction>> topBiggestTransactions(
            @RequestParam("bankAccountIds") List<String> bankAccountIds,
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "endDate", required = false) String endDateStr,
            @RequestParam(value = "limit", defaultValue = "6") int limit) {
        LocalDateTime startDate = parseStartDate(startDateStr);
        LocalDateTime endDate = parseEndDate(endDateStr);
        return ResponseEntity.ok(transactionService.getTopBiggestTransactionsByBankAccountIds(bankAccountIds, startDate, endDate, limit));
    }

    /**
     * Get category variance for a group (compare current vs previous period)
     */
    @GetMapping("/variance")
    public ResponseEntity<TransactionService.CategoryVariance> categoryVariance(
            @RequestParam("bankAccountIds") List<String> bankAccountIds,
            @RequestParam("type") String type, // expense | income
            @RequestParam(value = "currentStartDate", required = false) String currentStartDateStr,
            @RequestParam(value = "currentEndDate", required = false) String currentEndDateStr,
            @RequestParam(value = "previousStartDate", required = false) String previousStartDateStr,
            @RequestParam(value = "previousEndDate", required = false) String previousEndDateStr) {
        LocalDateTime currentStart = parseStartDate(currentStartDateStr);
        LocalDateTime currentEnd = parseEndDate(currentEndDateStr);
        LocalDateTime previousStart = parseStartDate(previousStartDateStr);
        LocalDateTime previousEnd = parseEndDate(previousEndDateStr);
        return ResponseEntity.ok(transactionService.getCategoryVarianceByBankAccountIds(
                bankAccountIds, type, currentStart, currentEnd, previousStart, previousEnd));
    }

    /**
     * Get daily expense heatmap for a group
     */
    @GetMapping("/expense-heatmap")
    public ResponseEntity<TransactionService.ExpenseHeatmap> expenseHeatmap(
            @RequestParam("bankAccountIds") List<String> bankAccountIds,
            @RequestParam("year") int year,
            @RequestParam("month") int month,
            @RequestParam(value = "dailyLimit", required = false) Double manualDailyLimit) {
        return ResponseEntity.ok(transactionService.getDailyExpenseHeatmapByBankAccountIds(
                bankAccountIds, year, month, manualDailyLimit));
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



