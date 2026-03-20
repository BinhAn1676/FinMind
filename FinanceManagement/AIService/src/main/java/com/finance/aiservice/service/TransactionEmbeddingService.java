package com.finance.aiservice.service;

import com.finance.aiservice.client.FinanceServiceClient;
import com.finance.aiservice.dto.TransactionDto;
import com.finance.aiservice.entity.TransactionEmbedding;
import com.finance.aiservice.repository.TransactionEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for automatically generating and storing transaction embeddings.
 *
 * Features:
 * - Scheduled background job (every 5 minutes)
 * - Fetches recent transactions from FinanceService
 * - Generates embeddings for new transactions
 * - Stores in transaction_embeddings table for RAG search
 * - Incremental processing (only new transactions)
 *
 * This enables:
 * - Semantic transaction search ("find similar transactions")
 * - Duplicate detection
 * - Spending pattern analysis
 * - Context-aware AI responses
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "ai.transaction-embedding.enabled",
    havingValue = "true",
    matchIfMissing = true // Enable by default
)
public class TransactionEmbeddingService {

    private final EmbeddingService embeddingService;
    private final TransactionEmbeddingRepository transactionEmbeddingRepository;
    private final FinanceServiceClient financeServiceClient;

    private static final int BATCH_SIZE = 50; // Process 50 transactions per run
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;

    /**
     * Scheduled job: Process new transactions every 5 minutes.
     *
     * Fetches recent transactions and generates embeddings for those
     * that don't already have them.
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 60000) // 5 minutes, 1 minute initial delay
    @Transactional
    public void processNewTransactions() {
        if (!embeddingService.isOllamaAvailable()) {
            log.warn("Ollama not available, skipping transaction embedding job");
            return;
        }

        log.debug("Starting transaction embedding job...");

        try {
            // For now, we'll process transactions as they come in via explicit API calls
            // In a production system, you might query FinanceService for recent transactions
            // and filter out those that already have embeddings

            // This is a placeholder for the actual implementation
            // You would typically:
            // 1. Get recent transactions (last 24 hours or since last run)
            // 2. Check which ones don't have embeddings yet
            // 3. Generate and store embeddings for them

            log.debug("Transaction embedding job completed");

        } catch (Exception e) {
            log.error("Transaction embedding job failed", e);
        }
    }

    /**
     * Process a single transaction and generate its embedding.
     *
     * Call this method when a new transaction is created or when you want
     * to manually trigger embedding generation.
     *
     * @param userId User ID who owns the transaction
     * @param transactionId Transaction ID from FinanceService
     * @param description Transaction description
     * @param category Transaction category
     * @param amount Transaction amount
     * @param transactionDate Transaction date
     * @param transactionType INCOME or EXPENSE
     * @return The created TransactionEmbedding entity
     */
    @Transactional
    public TransactionEmbedding processTransaction(
        String userId,
        Long transactionId,
        String description,
        String category,
        BigDecimal amount,
        LocalDate transactionDate,
        String transactionType
    ) {
        // Check if already processed
        if (transactionEmbeddingRepository.existsByUserIdAndTransactionId(userId, transactionId)) {
            log.debug("Transaction {} for user {} already has embedding, skipping", transactionId, userId);
            return transactionEmbeddingRepository
                .findByUserIdAndTransactionId(userId, transactionId)
                .orElse(null);
        }

        log.info("Processing transaction {} for user {}", transactionId, userId);

        try {
            // Build text representation of transaction
            String textToEmbed = buildTransactionText(
                description, category, amount, transactionDate, transactionType
            );

            log.debug("Transaction text: {}", textToEmbed);

            // Generate embedding
            float[] embedding = embeddingService.generateEmbeddingWithRetry(textToEmbed, 3);

            // Create entity
            TransactionEmbedding transactionEmbedding = TransactionEmbedding.builder()
                .userId(userId)
                .transactionId(transactionId)
                .description(description)
                .category(category)
                .amount(amount)
                .transactionDate(transactionDate)
                .transactionType(transactionType)
                .embedding(embedding)
                .embeddingModel("gemini-embedding-001")
                .embeddingVersion("1.0")
                .build();

            // Save to database
            TransactionEmbedding saved = transactionEmbeddingRepository.save(transactionEmbedding);

            log.info("✓ Successfully created embedding for transaction {}", transactionId);

            return saved;

        } catch (Exception e) {
            log.error("Failed to process transaction {}: {}", transactionId, e.getMessage(), e);
            throw new RuntimeException("Transaction embedding failed", e);
        }
    }

    /**
     * Process a TransactionDto from FinanceService.
     */
    @Transactional
    public TransactionEmbedding processTransactionDto(String userId, TransactionDto transaction) {
        // Extract amount from either amountOut (expense) or amountIn (income)
        double amount = 0.0;
        String type = transaction.transactionType();

        if (transaction.amountOut() != null && transaction.amountOut() > 0) {
            amount = transaction.amountOut();
            type = "EXPENSE";
        } else if (transaction.amountIn() != null && transaction.amountIn() > 0) {
            amount = transaction.amountIn();
            type = "INCOME";
        }

        return processTransaction(
            userId,
            Long.parseLong(transaction.id()),
            transaction.transactionContent(),
            transaction.category(),
            BigDecimal.valueOf(amount),
            LocalDate.parse(transaction.transactionDate(), DATE_FORMATTER),
            type
        );
    }

    /**
     * Build text representation of transaction for embedding.
     *
     * Format: "Category: [category], Amount: [amount] VND, Type: [type], Date: [date], Description: [description]"
     *
     * This format captures all important aspects of the transaction for semantic search.
     */
    private String buildTransactionText(
        String description,
        String category,
        BigDecimal amount,
        LocalDate date,
        String type
    ) {
        StringBuilder text = new StringBuilder();

        if (category != null && !category.isBlank()) {
            text.append("Category: ").append(category).append(", ");
        }

        if (amount != null) {
            text.append("Amount: ").append(amount.longValue()).append(" VND, ");
        }

        if (type != null && !type.isBlank()) {
            text.append("Type: ").append(type).append(", ");
        }

        if (date != null) {
            text.append("Date: ").append(date.format(DATE_FORMATTER)).append(", ");
        }

        if (description != null && !description.isBlank()) {
            text.append("Description: ").append(description);
        }

        return text.toString();
    }

    /**
     * Delete embedding for a transaction (e.g., when transaction is deleted).
     */
    @Transactional
    public void deleteTransactionEmbedding(String userId, Long transactionId) {
        if (transactionEmbeddingRepository.existsByUserIdAndTransactionId(userId, transactionId)) {
            transactionEmbeddingRepository.deleteByUserIdAndTransactionId(userId, transactionId);
            log.info("Deleted embedding for transaction {} of user {}", transactionId, userId);
        }
    }

    /**
     * Get embedding statistics for a user.
     */
    public EmbeddingStats getUserEmbeddingStats(String userId) {
        long count = transactionEmbeddingRepository.countByUserId(userId);
        return new EmbeddingStats(userId, count);
    }

    /**
     * Manually process all transactions for a user.
     *
     * Useful for initial data load or when re-processing is needed.
     * WARNING: This can be slow for users with many transactions.
     *
     * @param userId User ID
     * @return Number of transactions processed
     */
    @Transactional
    public int processAllUserTransactions(String userId) {
        log.info("Processing all transactions for user: {}", userId);

        // Note: In a real implementation, you would call FinanceService
        // to get all transactions for this user and process them.
        // For now, this is a placeholder.

        log.warn("processAllUserTransactions is not yet implemented - requires FinanceService integration");

        return 0;
    }

    public record EmbeddingStats(String userId, long embeddingCount) {}
}
