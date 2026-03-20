package com.finance.aiservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing financial advice/knowledge with vector embedding.
 *
 * Used for RAG (Retrieval-Augmented Generation) to provide context-aware
 * financial advice based on semantic similarity.
 */
@Entity
@Table(name = "financial_knowledge")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialKnowledge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String topic;

    @Column(length = 100)
    private String category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * Vector embedding (768 dimensions for gemini-embedding-001)
     * Stored as PostgreSQL vector type using pgvector extension
     */
    @Column(nullable = false, columnDefinition = "vector(768)")
    @org.hibernate.annotations.Type(com.finance.aiservice.config.VectorType.class)
    private float[] embedding;

    @Column(length = 10)
    private String language;

    @Column(columnDefinition = "TEXT[]")
    @JdbcTypeCode(SqlTypes.ARRAY)
    private String[] tags;

    @Column(length = 255)
    private String source;

    @Column(name = "confidence_score", columnDefinition = "NUMERIC")
    private Double confidenceScore;

    @Column(name = "is_active")
    private Boolean isActive;

    @Column
    private Integer priority;

    @Column(columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (isActive == null) {
            isActive = true;
        }
        if (priority == null) {
            priority = 0;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
