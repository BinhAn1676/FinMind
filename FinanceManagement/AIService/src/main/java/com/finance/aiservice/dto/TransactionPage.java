package com.finance.aiservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * DTO for paginated transaction response from FinanceService.
 *
 * Maps to Spring Data Page<Transaction> structure.
 */
public record TransactionPage(
    @JsonProperty("content")
    List<TransactionDto> content,

    @JsonProperty("totalElements")
    long totalElements,

    @JsonProperty("totalPages")
    int totalPages,

    @JsonProperty("size")
    int size,

    @JsonProperty("number")
    int number,

    @JsonProperty("first")
    boolean first,

    @JsonProperty("last")
    boolean last,

    @JsonProperty("empty")
    boolean empty
) {
}
