package com.finance.aiservice.engine;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Result of financial forecasting analysis.
 *
 * Contains predictions for:
 * - Next month expense/income
 * - Future balance
 * - Cash flow warnings
 * - Explainable AI reasoning
 */
@Data
@Builder
public class ForecastResult {

    /**
     * Overall prediction for next period.
     */
    @Data
    @Builder
    public static class Prediction {
        private String category;          // Category name (or "TOTAL" for overall)
        private Double predictedAmount;   // Predicted amount for next period
        private Double currentAmount;     // Current period amount (for comparison)
        private Double changePercent;     // Percentage change (predicted vs current)
        private String trend;             // "INCREASING", "DECREASING", "STABLE"
        private Double confidence;        // Confidence score (0.0-1.0)
    }

    /**
     * Cash flow warning.
     */
    @Data
    @Builder
    public static class CashFlowWarning {
        private String severity;          // "LOW", "MEDIUM", "HIGH"
        private String message;           // Warning message
        private Double projectedDeficit;  // Projected deficit amount (if negative)
        private String recommendation;    // What user should do
    }

    private boolean success;
    private String forecastPeriod;                  // e.g., "NEXT_MONTH", "NEXT_QUARTER"
    private Prediction overallPrediction;           // Overall expense/income prediction
    private List<Prediction> categoryPredictions;   // Per-category predictions
    private Double projectedBalance;                // Estimated future balance
    private CashFlowWarning cashFlowWarning;        // Warning if cash flow is negative
    private String explanation;                     // Explainable AI reasoning
    private Double confidence;                      // Overall confidence (0.0-1.0)
    private String errorMessage;

    public static ForecastResult error(String error) {
        return ForecastResult.builder()
            .success(false)
            .errorMessage(error)
            .build();
    }
}
