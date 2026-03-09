package com.finance.aiservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Map;

/**
 * Summary of user's transactions for a time period.
 * Returned by FinanceService.
 * 
 * Matches FinanceService TransactionSummary structure:
 * - Double for amounts (totalIncome, totalExpense, netAmount)
 * - long for transactionCount
 * - netAmount (not netBalance)
 * - averageAmount (optional)
 * 
 * Note: Fields like userId, expenseByCategory, incomeByCategory, startDate, endDate
 * are not returned by FinanceService but kept for compatibility (will be null).
 */
@Builder
public record TransactionSummaryDto(

    @JsonProperty("userId")
    String userId,  // Not returned by FinanceService, will be null

    @JsonProperty("totalIncome")
    Double totalIncome,  // Changed from Long to Double to match FinanceService

    @JsonProperty("totalExpense")
    Double totalExpense,  // Changed from Long to Double to match FinanceService

    @JsonProperty("netAmount")
    @JsonAlias("netBalance")  // Accept both netAmount (from FinanceService) and netBalance (for backward compatibility)
    Double netAmount,  // Changed from netBalance to netAmount to match FinanceService

    @JsonProperty("expenseByCategory")
    Map<String, Long> expenseByCategory,  // Not returned by FinanceService, will be null

    @JsonProperty("incomeByCategory")
    Map<String, Long> incomeByCategory,  // Not returned by FinanceService, will be null

    @JsonProperty("transactionCount")
    Long transactionCount,  // Changed from Integer to Long to match FinanceService (long)

    @JsonProperty("averageAmount")
    Double averageAmount,  // Added to match FinanceService

    @JsonProperty("startDate")
    String startDate,  // Not returned by FinanceService, will be null

    @JsonProperty("endDate")
    String endDate  // Not returned by FinanceService, will be null

) {
    /**
     * Get netAmount (for backward compatibility with code that might use netBalance).
     * This method provides a consistent way to access the net amount.
     */
    public Double getNetAmount() {
        return netAmount;
    }

    /**
     * Alias for getNetAmount() for backward compatibility.
     * @deprecated Use getNetAmount() or netAmount() instead
     */
    @Deprecated
    public Double getNetBalance() {
        return netAmount;
    }
}
