package com.finance.aiservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Individual transaction data from FinanceService.
 * Maps to Transaction entity from FinanceService MongoDB collection.
 */
@Builder
public record TransactionDto(

    @JsonProperty("id")
    String id,

    @JsonProperty("userId")
    String userId,

    @JsonProperty("amountOut")
    Double amountOut,  // Expense amount (null if income)

    @JsonProperty("amountIn")
    Double amountIn,  // Income amount (null if expense)

    @JsonProperty("transactionType")
    String transactionType,  // "income" or "expense"

    @JsonProperty("category")
    String category,

    @JsonProperty("transactionContent")
    String transactionContent,  // Transaction description

    @JsonProperty("bankBrandName")
    String bankBrandName,  // Bank/merchant name

    @JsonProperty("transactionDate")
    String transactionDate,  // ISO format LocalDateTime

    @JsonProperty("accountNumber")
    String accountNumber,  // Masked: ****1234

    @JsonProperty("bankAccountId")
    String bankAccountId,

    @JsonProperty("referenceNumber")
    String referenceNumber,

    @JsonProperty("accumulated")
    Double accumulated

) {}
