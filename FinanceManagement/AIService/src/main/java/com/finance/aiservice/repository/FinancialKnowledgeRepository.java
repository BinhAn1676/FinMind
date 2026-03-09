package com.finance.aiservice.repository;

import com.finance.aiservice.entity.FinancialKnowledge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for FinancialKnowledge entity.
 *
 * Provides methods for:
 * - CRUD operations
 * - Vector similarity search
 * - Category and tag filtering
 */
@Repository
public interface FinancialKnowledgeRepository extends JpaRepository<FinancialKnowledge, UUID> {

    /**
     * Find all active financial knowledge entries.
     */
    List<FinancialKnowledge> findByIsActiveTrue();

    /**
     * Find entries by category.
     */
    List<FinancialKnowledge> findByCategoryAndIsActiveTrue(String category);

    /**
     * Find entries by language.
     */
    List<FinancialKnowledge> findByLanguageAndIsActiveTrue(String language);

    /**
     * Find similar financial knowledge using vector cosine similarity.
     *
     * Note: Using native query because JPA doesn't support pgvector operations.
     * The query embedding should be formatted as a PostgreSQL vector string: '[0.1,0.2,...]'
     *
     * @param queryEmbedding Query vector formatted as PostgreSQL vector string
     * @param limit Maximum number of results
     * @return List of financial knowledge entries ordered by similarity (most similar first)
     */
    @Query(value = """
        SELECT * FROM financial_knowledge
        WHERE is_active = true
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<FinancialKnowledge> findSimilar(
        @Param("queryEmbedding") String queryEmbedding,
        @Param("limit") int limit
    );

    /**
     * Find similar entries with category filter.
     */
    @Query(value = """
        SELECT * FROM financial_knowledge
        WHERE is_active = true
          AND category = :category
        ORDER BY embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<FinancialKnowledge> findSimilarByCategory(
        @Param("queryEmbedding") String queryEmbedding,
        @Param("category") String category,
        @Param("limit") int limit
    );

    /**
     * Find entries with similarity score above threshold.
     *
     * Returns entries with cosine similarity score (1 - distance) >= minSimilarity
     */
    @Query(value = """
        SELECT fk.*, (1 - (fk.embedding <=> CAST(:queryEmbedding AS vector))) as similarity_score
        FROM financial_knowledge fk
        WHERE fk.is_active = true
          AND (1 - (fk.embedding <=> CAST(:queryEmbedding AS vector))) >= :minSimilarity
        ORDER BY fk.embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<FinancialKnowledge> findSimilarWithMinScore(
        @Param("queryEmbedding") String queryEmbedding,
        @Param("minSimilarity") double minSimilarity,
        @Param("limit") int limit
    );
}
