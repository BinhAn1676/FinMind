package com.finance.aiservice.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Entity representing a transaction with its vector embedding.
 *
 * Used for RAG to enable semantic search over transactions:
 * - Find similar past transactions
 * - Detect duplicates
 * - Identify spending patterns
 * - Provide context for AI responses
 */
@Entity
@Table(
    name = "transaction_embeddings",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "transaction_id"})
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Column(name = "transaction_id", nullable = false)
    private Long transactionId;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "transaction_type", length = 20)
    private String transactionType; // INCOME, EXPENSE

    /**
     * Vector embedding (768 dimensions for gemini-embedding-001)
     * Generated from: category + amount + description + date
     */
    @Column(nullable = false, columnDefinition = "vector(768)")
    @org.hibernate.annotations.Type(com.finance.aiservice.config.VectorType.class)
    private float[] embedding;

    @Column(name = "embedding_model", length = 50, nullable = false)
    private String embeddingModel;

    @Column(name = "embedding_version", length = 20)
    private String embeddingVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (embeddingModel == null) {
            embeddingModel = "gemini-embedding-001";
        }
        if (embeddingVersion == null) {
            embeddingVersion = "1.0";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
