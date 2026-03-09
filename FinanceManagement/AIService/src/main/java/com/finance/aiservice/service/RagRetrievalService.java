package com.finance.aiservice.service;

import com.finance.aiservice.entity.FinancialKnowledge;
import com.finance.aiservice.entity.TransactionEmbedding;
import com.finance.aiservice.repository.FinancialKnowledgeRepository;
import com.finance.aiservice.repository.TransactionEmbeddingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG (Retrieval Augmented Generation) Service
 *
 * Performs semantic search in vector databases to retrieve relevant context
 * for AI prompts and user queries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final FinancialKnowledgeRepository financialKnowledgeRepository;
    private final TransactionEmbeddingRepository transactionEmbeddingRepository;
    private final EmbeddingService embeddingService;

    /**
     * Convert float[] embedding to PostgreSQL vector string format.
     * Example: [0.1, 0.2, 0.3] → "[0.1,0.2,0.3]"
     */
    private String embeddingToString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    /**
     * Search for relevant financial advice based on user query.
     *
     * @param query User's natural language query
     * @param limit Max number of results (default: 3)
     * @param minSimilarity Minimum similarity score (0.0 - 1.0, default: 0.7)
     * @return List of relevant financial knowledge entries
     */
    public List<FinancialKnowledge> searchFinancialAdvice(String query, Integer limit, Double minSimilarity) {
        try {
            // Generate embedding for query
            float[] queryEmbedding = embeddingService.generateEmbedding(query);

            int resultLimit = limit != null ? limit : 3;
            double similarityThreshold = minSimilarity != null ? minSimilarity : 0.7;

            log.info("Searching financial advice for query: '{}' (limit: {}, minSimilarity: {})",
                query, resultLimit, similarityThreshold);

            // Use vector similarity search
            List<FinancialKnowledge> results = financialKnowledgeRepository.findSimilarWithMinScore(
                embeddingToString(queryEmbedding),
                similarityThreshold,
                resultLimit
            );

            log.info("Found {} relevant financial advice entries", results.size());
            return results;

        } catch (Exception e) {
            log.error("Error searching financial advice: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Search for relevant financial advice by category.
     *
     * @param query User's query
     * @param category Category to filter by (e.g., "Saving", "Budgeting")
     * @param limit Max results
     * @return List of relevant entries in the category
     */
    public List<FinancialKnowledge> searchFinancialAdviceByCategory(
            String query,
            String category,
            Integer limit
    ) {
        try {
            float[] queryEmbedding = embeddingService.generateEmbedding(query);
            int resultLimit = limit != null ? limit : 3;

            log.info("Searching financial advice in category '{}' for query: '{}'", category, query);

            List<FinancialKnowledge> results = financialKnowledgeRepository.findSimilarByCategory(
                embeddingToString(queryEmbedding),
                category,
                resultLimit
            );

            log.info("Found {} relevant entries in category '{}'", results.size(), category);
            return results;

        } catch (Exception e) {
            log.error("Error searching by category: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find similar transactions for a given transaction.
     * Useful for detecting patterns or duplicates.
     *
     * @param userId User ID
     * @param transactionId Transaction to find similar ones for
     * @param limit Max results
     * @return List of similar transactions
     */
    public List<TransactionEmbedding> findSimilarTransactions(
            String userId,
            Long transactionId,
            Integer limit
    ) {
        try {
            // Get the source transaction embedding
            var sourceTransaction = transactionEmbeddingRepository.findByUserIdAndTransactionId(userId, transactionId)
                .orElse(null);

            if (sourceTransaction == null) {
                log.warn("Transaction {} not found for user {}", transactionId, userId);
                return List.of();
            }

            int resultLimit = limit != null ? limit : 5;

            log.info("Finding similar transactions for transaction {} (user: {})", transactionId, userId);

            // Search for similar transactions (excluding the source transaction)
            List<TransactionEmbedding> results = transactionEmbeddingRepository.findSimilar(
                userId,
                embeddingToString(sourceTransaction.getEmbedding()),
                resultLimit + 1  // +1 because source will be included
            );

            // Remove the source transaction from results
            results.removeIf(t -> t.getTransactionId().equals(transactionId));

            // Limit to requested size
            if (results.size() > resultLimit) {
                results = results.subList(0, resultLimit);
            }

            log.info("Found {} similar transactions", results.size());
            return results;

        } catch (Exception e) {
            log.error("Error finding similar transactions: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find similar transactions by description query.
     *
     * @param userId User ID
     * @param descriptionQuery Natural language description
     * @param limit Max results
     * @return List of matching transactions
     */
    public List<TransactionEmbedding> searchTransactionsByDescription(
            String userId,
            String descriptionQuery,
            Integer limit
    ) {
        try {
            float[] queryEmbedding = embeddingService.generateEmbedding(descriptionQuery);
            int resultLimit = limit != null ? limit : 5;

            log.info("Searching transactions for user {} with query: '{}'", userId, descriptionQuery);

            List<TransactionEmbedding> results = transactionEmbeddingRepository.findSimilar(
                userId,
                embeddingToString(queryEmbedding),
                resultLimit
            );

            log.info("Found {} matching transactions", results.size());
            return results;

        } catch (Exception e) {
            log.error("Error searching transactions by description: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Find similar transactions by category.
     * For date filtering, use searchTransactionsByDescription with date-filtered embeddings.
     *
     * @param userId User ID
     * @param query Query string
     * @param category Category to filter
     * @param limit Max results
     * @return List of similar transactions in category
     */
    public List<TransactionEmbedding> searchTransactionsByCategory(
            String userId,
            String query,
            String category,
            Integer limit
    ) {
        try {
            float[] queryEmbedding = embeddingService.generateEmbedding(query);
            int resultLimit = limit != null ? limit : 5;

            log.info("Searching transactions for user {} in category '{}'",
                userId, category);

            List<TransactionEmbedding> results = transactionEmbeddingRepository.findSimilarByCategory(
                userId,
                embeddingToString(queryEmbedding),
                category,
                resultLimit
            );

            log.info("Found {} matching transactions", results.size());
            return results;

        } catch (Exception e) {
            log.error("Error searching transactions by category: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Detect potential duplicate transactions similar to a given transaction.
     *
     * @param userId User ID
     * @param transactionId Transaction to check for duplicates
     * @return List of potential duplicate transactions (very high similarity >= 0.95)
     */
    public List<TransactionEmbedding> detectPotentialDuplicates(
            String userId,
            Long transactionId
    ) {
        try {
            // Get the source transaction embedding
            var sourceTransaction = transactionEmbeddingRepository.findByUserIdAndTransactionId(userId, transactionId)
                .orElse(null);

            if (sourceTransaction == null) {
                log.warn("Transaction {} not found for user {}", transactionId, userId);
                return List.of();
            }

            log.info("Detecting duplicate transactions for user {} (transaction: {})",
                userId, transactionId);

            // Use repository method with >= 0.95 similarity threshold
            List<TransactionEmbedding> results = transactionEmbeddingRepository.findPotentialDuplicates(
                userId,
                embeddingToString(sourceTransaction.getEmbedding()),
                transactionId  // Exclude the source transaction
            );

            log.info("Found {} potential duplicates", results.size());
            return results;

        } catch (Exception e) {
            log.error("Error detecting duplicates: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Build context string from financial knowledge for AI prompts.
     *
     * @param query User's query
     * @param maxEntries Max knowledge entries to include
     * @return Formatted context string
     */
    public String buildFinancialAdviceContext(String query, Integer maxEntries) {
        List<FinancialKnowledge> relevant = searchFinancialAdvice(query, maxEntries, 0.7);

        if (relevant.isEmpty()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("Relevant Financial Advice:\n\n");

        for (int i = 0; i < relevant.size(); i++) {
            FinancialKnowledge knowledge = relevant.get(i);
            context.append(String.format("%d. %s\n", i + 1, knowledge.getTopic()));
            context.append(String.format("   %s\n\n", knowledge.getContent()));
        }

        return context.toString();
    }
}
