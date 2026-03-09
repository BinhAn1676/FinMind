package com.finance.aiservice.engine;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result from financial analysis.
 */
@Data
@Builder
public class AnalysisResult {

    /**
     * Engine that produced this result.
     */
    public enum EngineType {
        RULE_BASED,   // Traditional rule-based analysis
        AI_REASONING, // AI-powered reasoning only
        HYBRID        // Combined rule-based + AI reasoning
    }

    /**
     * Individual insight from analysis.
     */
    @Data
    @Builder
    public static class Insight {
        private String category;      // Category related to insight
        private String message;       // Human-readable insight message
        private String severity;      // HIGH, MEDIUM, LOW
        private Double amount;        // Related amount (if applicable)
        private String recommendation; // Suggested action
    }

    /**
     * Actionable recommendation.
     */
    @Data
    @Builder
    public static class Recommendation {
        private String title;         // Short recommendation title
        private String description;   // Detailed explanation
        private Integer impactScore;  // Impact score (0-100, higher = more impactful)
        private String category;      // Related category
    }

    /**
     * Whether analysis succeeded.
     */
    private boolean success;

    /**
     * Type of analysis performed.
     */
    private AnalysisRequest.AnalysisType analysisType;

    /**
     * Primary insight summary.
     */
    private String insight;

    /**
     * Detailed insights list.
     */
    private List<Insight> insights;

    /**
     * Actionable recommendations.
     */
    private List<Recommendation> recommendations;

    /**
     * Financial health score (0-100) if applicable.
     */
    private Integer healthScore;

    /**
     * Engine that produced this result.
     */
    private EngineType engineType;

    /**
     * Confidence level (0.0-1.0).
     */
    private Double confidence;

    /**
     * Error message if analysis failed.
     */
    private String errorMessage;

    /**
     * AI-generated narrative assessment (from AiReasoningEngine).
     * Provides a comprehensive natural-language summary beyond what rules can capture.
     */
    private String aiNarrative;

    /**
     * Whether AI enhancement was applied to this result.
     */
    private boolean aiEnhanced;
}
