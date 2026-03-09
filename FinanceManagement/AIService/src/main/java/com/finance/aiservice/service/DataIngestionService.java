package com.finance.aiservice.service;

import com.finance.aiservice.entity.FinancialKnowledge;
import com.finance.aiservice.repository.FinancialKnowledgeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

/**
 * Service for ingesting and processing data for RAG.
 *
 * Responsibilities:
 * - Update placeholder embeddings in financial_knowledge table
 * - Run on application startup to ensure all data has real embeddings
 * - Can be disabled via configuration for production deployments
 *
 * This service runs ONCE when AIService starts to replace zero-vector
 * placeholders with real embeddings from Ollama.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "ai.data-ingestion.enabled",
    havingValue = "true",
    matchIfMissing = true // Enable by default
)
public class DataIngestionService {

    private final EmbeddingService embeddingService;
    private final FinancialKnowledgeRepository financialKnowledgeRepository;

    /**
     * Update financial knowledge embeddings on application startup.
     *
     * This method:
     * 1. Checks if Ollama is available
     * 2. Fetches all financial knowledge entries
     * 3. Identifies entries with zero/placeholder embeddings
     * 4. Generates real embeddings from topic + content
     * 5. Updates database with real vectors
     *
     * Runs automatically on startup (@PostConstruct).
     */
    @PostConstruct
    @Transactional
    public void updateFinancialKnowledgeEmbeddings() {
        log.info("========================================");
        log.info("Starting Financial Knowledge Data Ingestion");
        log.info("========================================");

        // Check if Ollama is available
        if (!embeddingService.isOllamaAvailable()) {
            log.error("Ollama is not available! Skipping data ingestion.");
            log.error("Please ensure Ollama is running at: http://localhost:11434");
            log.error("And model 'nomic-embed-text' is pulled");
            return;
        }

        try {
            // Fetch all financial knowledge entries
            List<FinancialKnowledge> allEntries = financialKnowledgeRepository.findAll();
            log.info("Found {} financial knowledge entries in database", allEntries.size());

            if (allEntries.isEmpty()) {
                log.warn("No financial knowledge entries found. Database might be empty.");
                return;
            }

            // Filter entries that need embedding update (zero vectors or null)
            List<FinancialKnowledge> entriesToUpdate = allEntries.stream()
                .filter(this::needsEmbeddingUpdate)
                .toList();

            log.info("{} entries need embedding updates", entriesToUpdate.size());

            if (entriesToUpdate.isEmpty()) {
                log.info("All financial knowledge entries already have embeddings. Skipping update.");
                return;
            }

            // Process each entry
            int successCount = 0;
            int failureCount = 0;

            for (FinancialKnowledge entry : entriesToUpdate) {
                try {
                    updateSingleEntry(entry);
                    successCount++;
                } catch (Exception e) {
                    failureCount++;
                    log.error("Failed to update embedding for entry '{}': {}",
                        entry.getTopic(), e.getMessage());
                }
            }

            log.info("========================================");
            log.info("Data Ingestion Complete!");
            log.info("Success: {} entries", successCount);
            log.info("Failed: {} entries", failureCount);
            log.info("========================================");

        } catch (Exception e) {
            log.error("Data ingestion failed with unexpected error", e);
        }
    }

    /**
     * Check if entry needs embedding update.
     *
     * An entry needs update if:
     * - Embedding is null
     * - Embedding is all zeros (placeholder)
     * - Embedding has wrong dimensions
     */
    private boolean needsEmbeddingUpdate(FinancialKnowledge entry) {
        float[] embedding = entry.getEmbedding();

        if (embedding == null) {
            return true;
        }

        if (embedding.length != 768) {
            log.warn("Entry '{}' has wrong embedding dimensions: {} (expected 768)",
                entry.getTopic(), embedding.length);
            return true;
        }

        // Check if all zeros (placeholder)
        boolean allZeros = true;
        for (float val : embedding) {
            if (Math.abs(val) > 0.0001f) { // Use small epsilon for float comparison
                allZeros = false;
                break;
            }
        }

        return allZeros;
    }

    /**
     * Update embedding for a single entry.
     */
    private void updateSingleEntry(FinancialKnowledge entry) {
        log.info("Updating embedding for: '{}'", entry.getTopic());

        // Combine topic and content for richer semantic embedding
        String textToEmbed = String.format(
            "%s\n\n%s",
            entry.getTopic(),
            entry.getContent()
        );

        log.debug("Text to embed (length: {} chars): {}",
            textToEmbed.length(),
            textToEmbed.length() > 100 ? textToEmbed.substring(0, 100) + "..." : textToEmbed
        );

        // Generate embedding with retry
        float[] embedding = embeddingService.generateEmbeddingWithRetry(textToEmbed, 3);

        // Update entity
        entry.setEmbedding(embedding);

        // Save to database
        financialKnowledgeRepository.save(entry);

        log.info("✓ Successfully updated embedding for: '{}' ({} dimensions)",
            entry.getTopic(), embedding.length);
    }

    /**
     * Manually trigger data ingestion (useful for testing or re-processing).
     *
     * @return Number of entries updated
     */
    public int manuallyTriggerIngestion() {
        log.info("Manual data ingestion triggered");

        List<FinancialKnowledge> allEntries = financialKnowledgeRepository.findAll();
        List<FinancialKnowledge> entriesToUpdate = allEntries.stream()
            .filter(this::needsEmbeddingUpdate)
            .toList();

        int successCount = 0;
        for (FinancialKnowledge entry : entriesToUpdate) {
            try {
                updateSingleEntry(entry);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to update entry: {}", entry.getTopic(), e);
            }
        }

        log.info("Manual ingestion complete: {} entries updated", successCount);
        return successCount;
    }

    /**
     * Get statistics about embedding status.
     */
    public EmbeddingStats getEmbeddingStats() {
        List<FinancialKnowledge> allEntries = financialKnowledgeRepository.findAll();

        long total = allEntries.size();
        long needsUpdate = allEntries.stream()
            .filter(this::needsEmbeddingUpdate)
            .count();
        long hasEmbedding = total - needsUpdate;

        return new EmbeddingStats(total, hasEmbedding, needsUpdate);
    }

    public record EmbeddingStats(long total, long withEmbedding, long needsUpdate) {
        public double percentComplete() {
            return total > 0 ? (withEmbedding * 100.0 / total) : 0;
        }
    }
}
