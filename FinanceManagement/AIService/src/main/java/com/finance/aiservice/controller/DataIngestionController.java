package com.finance.aiservice.controller;

import com.finance.aiservice.service.DataIngestionService;
import com.finance.aiservice.service.EmbeddingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for manually triggering and monitoring data ingestion.
 * Useful for testing and debugging Phase 2 RAG implementation.
 */
@RestController
@RequestMapping("/api/admin/data-ingestion")
@RequiredArgsConstructor
@Slf4j
public class DataIngestionController {

    private final DataIngestionService dataIngestionService;
    private final EmbeddingService embeddingService;

    /**
     * Manually trigger data ingestion for financial knowledge.
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerIngestion() {
        try {
            log.info("Manual data ingestion triggered via API");
            int updated = dataIngestionService.manuallyTriggerIngestion();

            return ResponseEntity.ok(Map.of(
                "success", true,
                "entriesUpdated", updated,
                "message", "Data ingestion completed successfully"
            ));
        } catch (Exception e) {
            log.error("Manual data ingestion failed", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage(),
                "message", "Data ingestion failed"
            ));
        }
    }

    /**
     * Get embedding statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        try {
            DataIngestionService.EmbeddingStats stats = dataIngestionService.getEmbeddingStats();

            return ResponseEntity.ok(Map.of(
                "total", stats.total(),
                "withEmbedding", stats.withEmbedding(),
                "needsUpdate", stats.needsUpdate(),
                "percentComplete", stats.percentComplete()
            ));
        } catch (Exception e) {
            log.error("Failed to get stats", e);
            return ResponseEntity.status(500).body(Map.of(
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Test embedding generation with a sample text.
     */
    @PostMapping("/test-embedding")
    public ResponseEntity<Map<String, Object>> testEmbedding(@RequestBody Map<String, String> request) {
        try {
            String text = request.getOrDefault("text", "Test text");

            long startTime = System.currentTimeMillis();
            float[] embedding = embeddingService.generateEmbedding(text);
            long duration = System.currentTimeMillis() - startTime;

            return ResponseEntity.ok(Map.of(
                "success", true,
                "dimensions", embedding.length,
                "durationMs", duration,
                "first3Values", new float[]{embedding[0], embedding[1], embedding[2]},
                "message", "Embedding generated successfully"
            ));
        } catch (Exception e) {
            log.error("Embedding test failed", e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }

    /**
     * Check if Ollama is available.
     */
    @GetMapping("/ollama-status")
    public ResponseEntity<Map<String, Object>> checkOllama() {
        boolean available = embeddingService.isOllamaAvailable();
        int dimensions = embeddingService.getEmbeddingDimensions();

        return ResponseEntity.ok(Map.of(
            "available", available,
            "dimensions", dimensions,
            "model", "nomic-embed-text"
        ));
    }
}
