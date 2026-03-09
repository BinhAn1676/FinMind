package com.finance.aiservice.client;

import com.finance.aiservice.dto.CategoryDto;
import com.finance.aiservice.dto.TransactionSummaryDto;
import com.finance.aiservice.dto.TransactionDto;
import com.finance.aiservice.dto.TransactionPage;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Feign Client for FinanceService integration.
 *
 * Used by AI function calling tools to fetch transaction data.
 * Uses Eureka service discovery - no hardcoded URL.
 *
 * Supports both personal (userId-based) and group (bankAccountIds-based) queries.
 * When bankAccountIds is provided, FinanceService filters transactions across those
 * specific bank accounts (used for group financial analysis).
 */
@FeignClient(name = "finances")
public interface FinanceServiceClient {

    /**
     * Get spending summary for a user within a time period.
     * When bankAccountIds is provided, returns group-level summary across those accounts.
     *
     * @param userId User ID (used when bankAccountIds is null)
     * @param startDate Start date (ISO format: 2024-01-01)
     * @param endDate End date (ISO format: 2024-01-31)
     * @param bankAccountIds Optional list of bank account IDs for group-level queries
     */
    @GetMapping("/api/v1/transactions/summary")
    ResponseEntity<TransactionSummaryDto> getSpendingSummary(
        @RequestParam("userId") String userId,
        @RequestParam("startDate") String startDate,
        @RequestParam("endDate") String endDate,
        @RequestParam(value = "bankAccountIds", required = false) List<String> bankAccountIds
    );

    /**
     * Get transaction history for a user with optional filters.
     * When bankAccountIds is provided, returns transactions across those accounts.
     *
     * @param userId User ID (used when bankAccountIds is null)
     * @param startDate Start date filter
     * @param endDate End date filter
     * @param textSearch Text search filter
     * @param category Category filter
     * @param transactionType Type filter: "income" or "expense"
     * @param sortBy Sort field
     * @param sortDirection Sort direction (ASC, DESC)
     * @param page Page number (0-indexed)
     * @param size Page size
     * @param bankAccountIds Optional list of bank account IDs for group-level queries
     */
    @GetMapping("/api/v1/transactions")
    ResponseEntity<TransactionPage> getTransactionHistory(
        @RequestParam("userId") String userId,
        @RequestParam(value = "startDate", required = false) String startDate,
        @RequestParam(value = "endDate", required = false) String endDate,
        @RequestParam(value = "textSearch", required = false) String textSearch,
        @RequestParam(value = "category", required = false) String category,
        @RequestParam(value = "transactionType", required = false) String transactionType,
        @RequestParam(value = "sortBy", defaultValue = "transactionDate") String sortBy,
        @RequestParam(value = "sortDirection", defaultValue = "DESC") String sortDirection,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "50") int size,
        @RequestParam(value = "bankAccountIds", required = false) List<String> bankAccountIds
    );

    /**
     * Get user's categories.
     *
     * @param userId User ID
     * @return List of user's categories
     */
    @GetMapping("/api/v1/categories")
    ResponseEntity<List<CategoryDto>> getUserCategories(
        @RequestParam("userId") String userId
    );

}
