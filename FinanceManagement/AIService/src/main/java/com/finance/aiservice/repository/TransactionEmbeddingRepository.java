package com.finance.aiservice.repository;

import com.finance.aiservice.entity.TransactionEmbedding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TransactionEmbedding entity.
 *
 * Provides methods for:
 * - CRUD operations
 * - Vector similarity search for transactions
 * - User-specific queries
 * - Duplicate detection
 */
@Repository
public interface TransactionEmbeddingRepository extends JpaRepository<TransactionEmbedding, UUID> {

    /**
     * Find embedding for specific transaction.
     */
    Optional<TransactionEmbedding> findByUserIdAndTransactionId(String userId, Long transactionId);

    /**
     * Check if transaction already has embedding.
     */
    boolean existsByUserIdAndTransactionId(String userId, Long transactionId);

    /**
     * Find all embeddings for a user.
     */
    List<TransactionEmbedding> findByUserId(String userId);

    /**
     * Find embeddings by category for a user.
     */
    List<TransactionEmbedding> findByUserIdAndCategory(String userId, String category);

    /**
     * Find similar transactions for a user using vector cosine similarity.
     *
     * @param userId User ID to search within
     * @param queryEmbedding Query vector formatted as PostgreSQL vector string '[0.1,0.2,...]'
     * @param limit Maximum number of results
     * @return List of transactions ordered by similarity (most similar first)
     */
    @Query(value = """
        SELECT * FROM transaction_embeddings
        WHERE user_id = :userId
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<TransactionEmbedding> findSimilar(
        @Param("userId") String userId,
        @Param("queryEmbedding") String queryEmbedding,
        @Param("limit") int limit
    );

    /**
     * Find similar transactions with category filter.
     */
    @Query(value = """
        SELECT * FROM transaction_embeddings
        WHERE user_id = :userId
          AND category = :category
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<TransactionEmbedding> findSimilarByCategory(
        @Param("userId") String userId,
        @Param("queryEmbedding") String queryEmbedding,
        @Param("category") String category,
        @Param("limit") int limit
    );

    /**
     * Find similar transactions within date range.
     */
    @Query(value = """
        SELECT * FROM transaction_embeddings
        WHERE user_id = :userId
          AND transaction_date BETWEEN :startDate AND :endDate
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<TransactionEmbedding> findSimilarInDateRange(
        @Param("userId") String userId,
        @Param("queryEmbedding") String queryEmbedding,
        @Param("startDate") LocalDate startDate,
        @Param("endDate") LocalDate endDate,
        @Param("limit") int limit
    );

    /**
     * Find similar transactions with minimum similarity threshold.
     *
     * Returns only transactions with cosine similarity >= minSimilarity
     */
    @Query(value = """
        SELECT te.*, (1 - (te.embedding <=> CAST(:queryEmbedding AS vector))) as similarity_score
        FROM transaction_embeddings te
        WHERE te.user_id = :userId
          AND (1 - (te.embedding <=> CAST(:queryEmbedding AS vector))) >= :minSimilarity
        ORDER BY te.embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<TransactionEmbedding> findSimilarWithMinScore(
        @Param("userId") String userId,
        @Param("queryEmbedding") String queryEmbedding,
        @Param("minSimilarity") double minSimilarity,
        @Param("limit") int limit
    );

    /**
     * Find potential duplicate transactions (very high similarity).
     *
     * Useful for detecting accidentally duplicated transactions.
     * Uses high similarity threshold (>= 0.95)
     */
    @Query(value = """
        SELECT te.*, (1 - (te.embedding <=> CAST(:queryEmbedding AS vector))) as similarity_score
        FROM transaction_embeddings te
        WHERE te.user_id = :userId
          AND te.transaction_id != :excludeTransactionId
          AND (1 - (te.embedding <=> CAST(:queryEmbedding AS vector))) >= 0.95
        ORDER BY te.embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT 5
        """, nativeQuery = true)
    List<TransactionEmbedding> findPotentialDuplicates(
        @Param("userId") String userId,
        @Param("queryEmbedding") String queryEmbedding,
        @Param("excludeTransactionId") Long excludeTransactionId
    );

    /**
     * Count embeddings for a user.
     */
    long countByUserId(String userId);

    /**
     * Delete embedding for specific transaction.
     */
    void deleteByUserIdAndTransactionId(String userId, Long transactionId);
}
