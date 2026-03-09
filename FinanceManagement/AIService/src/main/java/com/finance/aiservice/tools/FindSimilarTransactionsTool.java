package com.finance.aiservice.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.finance.aiservice.entity.TransactionEmbedding;
import com.finance.aiservice.service.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI Function Tool: Find Similar Transactions
 *
 * Enables AI to find transactions similar to a given transaction or description.
 * Uses semantic search to identify patterns in spending behavior.
 *
 * Example uses:
 * - Find similar past purchases
 * - Detect spending patterns
 * - Identify recurring expenses
 * - Help explain unusual transactions
 */
@Slf4j
@Component("findSimilarTransactions")
@Description("""
    Find transactions similar to a description or existing transaction.

    USE THIS WHEN user asks:
    - "Have I bought something like this before?" → Search by description
    - "Find similar transactions to this one" → Search by transaction ID
    - "When did I last spend on similar things?" → Pattern detection
    - "Show me similar expenses" → Spending pattern analysis

    This tool uses semantic search to find transactions with similar:
    - Categories
    - Descriptions
    - Merchants
    - Amounts (within range)

    Parameters:
    - userId: Required - User ID from context
    - description: Optional - Natural language description to search for
    - transactionId: Optional - Find transactions similar to this ID
    - limit: Optional - Max results (default: 5, max: 10)

    Note: Provide either 'description' OR 'transactionId', not both.

    Examples:
    - "Find similar to coffee purchases" → findSimilarTransactions(userId="1", description="coffee", limit=5)
    - "Similar to transaction 12345" → findSimilarTransactions(userId="1", transactionId=12345, limit=5)
    - "Past shopping expenses like this" → findSimilarTransactions(userId="1", description="shopping", limit=5)
    """)
@RequiredArgsConstructor
public class FindSimilarTransactionsTool implements Function<FindSimilarTransactionsTool.Request, FindSimilarTransactionsTool.Response> {

    private final RagRetrievalService ragRetrievalService;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public Response apply(Request request) {
        log.info("AI Tool called: findSimilarTransactions (userId: {}, description: {}, transactionId: {}, limit: {})",
            request.userId(), request.description(), request.transactionId(), request.limit());

        try {
            // Validate input
            if (request.userId() == null || request.userId().trim().isEmpty()) {
                return Response.error("userId is required");
            }

            if ((request.description() == null || request.description().trim().isEmpty()) &&
                request.transactionId() == null) {
                return Response.error("Either 'description' or 'transactionId' must be provided");
            }

            int limit = request.limit() != null ? request.limit() : 5;
            if (limit > 10) {
                limit = 10;
            }

            List<TransactionEmbedding> results;

            // Search by transaction ID or description
            if (request.transactionId() != null) {
                // Find similar to specific transaction
                results = ragRetrievalService.findSimilarTransactions(
                    request.userId(),
                    request.transactionId(),
                    limit
                );
            } else {
                // Search by description
                results = ragRetrievalService.searchTransactionsByDescription(
                    request.userId(),
                    request.description(),
                    limit
                );
            }

            if (results.isEmpty()) {
                return Response.success(
                    List.of(),
                    "No similar transactions found. This might be a unique expense."
                );
            }

            // Convert to simple DTO for AI
            List<SimilarTransaction> transactions = results.stream()
                .map(this::toSimilarTransaction)
                .collect(Collectors.toList());

            return Response.success(
                transactions,
                String.format("Found %d similar transactions", results.size())
            );

        } catch (Exception e) {
            log.error("Error in findSimilarTransactions tool: {}", e.getMessage(), e);
            return Response.error("Failed to find similar transactions: " + e.getMessage());
        }
    }

    private SimilarTransaction toSimilarTransaction(TransactionEmbedding embedding) {
        return new SimilarTransaction(
            embedding.getTransactionId(),
            embedding.getDescription(),
            embedding.getCategory(),
            formatAmount(embedding.getAmount()),
            embedding.getTransactionType(),
            embedding.getTransactionDate() != null
                ? embedding.getTransactionDate().format(DATE_FORMATTER)
                : null
        );
    }

    private String formatAmount(BigDecimal amount) {
        if (amount == null) {
            return "0 ₫";
        }
        return String.format("%,.0f ₫", amount);
    }

    /**
     * Request parameters for finding similar transactions.
     */
    public record Request(
        @JsonProperty(required = true)
        @JsonPropertyDescription("User ID from context")
        String userId,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Natural language description to search for (e.g., 'coffee', 'shopping', 'dining')")
        String description,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Transaction ID to find similar transactions for")
        Long transactionId,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Maximum number of similar transactions to return (default: 5, max: 10)")
        Integer limit
    ) {}

    /**
     * Response containing similar transactions.
     */
    public record Response(
        @JsonProperty("success")
        boolean success,

        @JsonProperty("transactions")
        List<SimilarTransaction> transactions,

        @JsonProperty("message")
        String message,

        @JsonProperty("error")
        String error
    ) {
        public static Response success(List<SimilarTransaction> transactions, String message) {
            return new Response(true, transactions, message, null);
        }

        public static Response error(String error) {
            return new Response(false, List.of(), null, error);
        }
    }

    /**
     * Single similar transaction (simplified for AI).
     */
    public record SimilarTransaction(
        @JsonProperty("transactionId")
        Long transactionId,

        @JsonProperty("description")
        String description,

        @JsonProperty("category")
        String category,

        @JsonProperty("amount")
        String amount,

        @JsonProperty("type")
        String type,

        @JsonProperty("date")
        String date
    ) {}
}
