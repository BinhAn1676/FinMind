package com.finance.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Analytics Dashboard Response DTOs
 *
 * These DTOs provide structured data for frontend visualizations:
 * - Financial health scores
 * - Spending structure charts
 * - Anomaly alerts
 * - Budget forecasts
 * - Pattern analysis
 */
public class AnalyticsDashboardResponse {

    /**
     * Financial Health Score Response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HealthScore {
        private Integer score;           // 0-100
        private String grade;            // A+, A, A-, B+, B, etc.
        private String status;           // "Xuất sắc", "Tốt", "Khá tốt", etc.
        private String message;          // Main insight message
        private List<String> strengths;  // Positive points
        private List<String> concerns;   // Areas to improve
        private String aiNarrative;      // AI-generated narrative assessment (from HybridEngine)
        private Boolean aiEnhanced;      // Whether AI reasoning was applied
    }

    /**
     * Spending Structure Category
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpendingCategory {
        private String name;             // Category name
        private Double actualPercent;    // Actual spending %
        private Double idealPercent;     // Ideal spending % (of income)
        private Double amount;           // Actual amount spent
        private Double idealAmount;      // Ideal amount based on income (idealPercent × income)
        private String status;           // "GOOD", "WARNING", "ALERT", "CRITICAL"
    }

    /**
     * Spending Structure Response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpendingStructure {
        private List<SpendingCategory> categories;
        private Double totalIncome;      // Total income for the period (for context)
        private Double totalExpense;     // Total expense for the period
        private String overallStatus;    // "HEALTHY", "NEEDS_ATTENTION", "CRITICAL"
        private String recommendation;   // Main recommendation
    }

    /**
     * Anomaly Alert
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnomalyAlert {
        private String transactionId;
        private String description;      // "Hadilao Hotpot"
        private Double amount;
        private String date;
        private String severity;         // "HIGH", "MEDIUM", "LOW"
        private String reason;           // "Cao hơn mức thường 250%"
        private String recommendation;
    }

    /**
     * Spending Pattern
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SpendingPattern {
        private String type;             // "TIME", "DAY", "BEHAVIOR"
        private String pattern;          // "20h-22h", "Thứ Bảy", "Impulse Buy"
        private String description;      // "Không gửi tiền nhắc nhở Shopping Online"
        private List<String> recommendations;
    }

    /**
     * Budget Forecast Timeline Point
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForecastPoint {
        private String period;           // "Week 1", "Week 2", etc.
        private Double balance;
        private Double expense;
        private Double income;
    }

    /**
     * Budget Forecast Response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BudgetForecast {
        private String forecastPeriod;   // "30 ngày tới"
        private Double projectedBalance;
        private Double projectedExpense;
        private String trend;            // "INCREASING", "DECREASING", "STABLE"
        private Double confidence;       // 0-100
        private List<ForecastPoint> timeline;
        private CashFlowWarning warning;
    }

    /**
     * Cash Flow Warning
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CashFlowWarning {
        private String severity;         // "LOW", "MEDIUM", "HIGH"
        private String message;
        private Double projectedDeficit;
        private String recommendation;
    }

    /**
     * Financial Discipline Recommendation
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DisciplineRecommendation {
        private String category;         // "Cắt giảm Mua sắm"
        private Double currentAmount;
        private Double targetAmount;
        private Double savingsPotential; // Potential savings
        private Integer priority;        // 1-5
    }

    /**
     * Complete Analytics Dashboard Response
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteDashboard {
        private HealthScore healthScore;
        private SpendingStructure spendingStructure;
        private List<AnomalyAlert> anomalies;
        private List<SpendingPattern> patterns;
        private BudgetForecast budgetForecast;
        private List<DisciplineRecommendation> recommendations;
        private String generatedAt;      // Timestamp
    }
}
