package com.finance.aiservice.engine;

import lombok.Builder;
import lombok.Data;

/**
 * Request for financial analysis from Hybrid Engine.
 */
@Data
@Builder
public class AnalysisRequest {

    /**
     * Type of analysis to perform.
     */
    public enum AnalysisType {
        SPENDING_ALERT,           // Detect unusual spending patterns
        SAVINGS_RECOMMENDATION,   // Suggest ways to save money
        FINANCIAL_HEALTH_SCORE,   // Calculate overall financial health (0-100)
        DEBT_IMPACT              // Analyze debt impact on finances
    }

    /**
     * Type of analysis requested.
     */
    private AnalysisType analysisType;

    /**
     * User ID to analyze.
     */
    private String userId;

    /**
     * Time period for analysis (e.g., "THIS_MONTH", "LAST_MONTH").
     */
    private String periodType;

    /**
     * Optional custom start date for analysis (format: "YYYY-MM-DD").
     * If provided with endDate, takes precedence over periodType.
     */
    private String startDate;

    /**
     * Optional custom end date for analysis (format: "YYYY-MM-DD").
     * If provided with startDate, takes precedence over periodType.
     */
    private String endDate;

    /**
     * Optional category filter for category-specific analysis.
     */
    private String category;
}
