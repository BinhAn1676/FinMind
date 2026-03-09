package com.finance.aiservice.engine;

import com.finance.aiservice.client.FinanceServiceClient;
import com.finance.aiservice.dto.TransactionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rule-based financial analysis engine.
 *
 * Implements traditional financial rules for:
 * - Spending alerts
 * - Savings recommendations
 * - Financial health scoring
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleBasedEngine {

    private final FinanceServiceClient financeServiceClient;

    /**
     * Analyze based on request type.
     */
    public AnalysisResult analyze(AnalysisRequest request) {
        log.info("Rule-based engine analyzing: type={}, userId={}",
            request.getAnalysisType(), request.getUserId());

        try {
            return switch (request.getAnalysisType()) {
                case SPENDING_ALERT -> analyzeSpendingAlerts(request);
                case SAVINGS_RECOMMENDATION -> analyzeSavingsRecommendations(request);
                case FINANCIAL_HEALTH_SCORE -> calculateFinancialHealthScore(request);
                case DEBT_IMPACT -> analyzeDebtImpact(request);
            };
        } catch (Exception e) {
            log.error("Error in rule-based analysis: {}", e.getMessage(), e);
            return AnalysisResult.builder()
                .success(false)
                .analysisType(request.getAnalysisType())
                .errorMessage("Lỗi phân tích: " + e.getMessage())
                .build();
        }
    }

    /**
     * Detect spending alerts - categories with unusually high expenses.
     */
    private AnalysisResult analyzeSpendingAlerts(AnalysisRequest request) {
        var transactions = fetchTransactions(request);

        // Group by category and sum amounts
        Map<String, Double> categoryTotals = new HashMap<>();
        for (TransactionDto tx : transactions) {
            if (tx.amountOut() != null && tx.amountOut() > 0) {
                String category = tx.category() != null ? tx.category() : "Khác";
                categoryTotals.merge(category, tx.amountOut(), Double::sum);
            }
        }

        log.info("Spending Alerts Analysis: found {} expense categories", categoryTotals.size());

        // Find top spending categories
        List<AnalysisResult.Insight> insights = categoryTotals.entrySet().stream()
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(3)
            .map(entry -> AnalysisResult.Insight.builder()
                .category(entry.getKey())
                .amount(entry.getValue())
                .message(String.format("Chi tiêu %s: %.0f ₫", entry.getKey(), entry.getValue()))
                .severity(entry.getValue() > 1000000 ? "HIGH" : "MEDIUM")
                .recommendation("Xem xét giảm chi tiêu trong danh mục này")
                .build())
            .collect(Collectors.toList());

        long highSeverityCount = insights.stream()
            .filter(i -> "HIGH".equals(i.getSeverity()))
            .count();
        log.info("✓ Generated {} spending insights ({} HIGH severity)", insights.size(), highSeverityCount);

        return AnalysisResult.builder()
            .success(true)
            .analysisType(AnalysisRequest.AnalysisType.SPENDING_ALERT)
            .insight(insights.isEmpty() ? "Không có cảnh báo chi tiêu"
                : String.format("Phát hiện %d danh mục chi tiêu cao", insights.size()))
            .insights(insights)
            .engineType(AnalysisResult.EngineType.RULE_BASED)
            .confidence(0.9)
            .build();
    }

    /**
     * Generate savings recommendations.
     */
    private AnalysisResult analyzeSavingsRecommendations(AnalysisRequest request) {
        List<AnalysisResult.Recommendation> recommendations = new ArrayList<>();

        // Simple rule-based recommendations
        recommendations.add(AnalysisResult.Recommendation.builder()
            .title("Theo dõi chi tiêu hàng ngày")
            .description("Ghi chép mọi khoản chi tiêu giúp bạn nhận thức rõ hơn về thói quen tiêu dùng")
            .impactScore(80)
            .build());

        recommendations.add(AnalysisResult.Recommendation.builder()
            .title("Áp dụng quy tắc 50/30/20")
            .description("50% cho nhu cầu thiết yếu, 30% cho mong muốn, 20% cho tiết kiệm")
            .impactScore(90)
            .build());

        return AnalysisResult.builder()
            .success(true)
            .analysisType(AnalysisRequest.AnalysisType.SAVINGS_RECOMMENDATION)
            .insight(String.format("Có %d khuyến nghị tiết kiệm", recommendations.size()))
            .recommendations(recommendations)
            .engineType(AnalysisResult.EngineType.RULE_BASED)
            .confidence(0.85)
            .build();
    }

    /**
     * Calculate financial health score (0-100).
     */
    private AnalysisResult calculateFinancialHealthScore(AnalysisRequest request) {
        var transactions = fetchTransactions(request);

        double totalIncome = transactions.stream()
            .filter(tx -> tx.amountIn() != null && tx.amountIn() > 0)
            .mapToDouble(TransactionDto::amountIn)
            .sum();

        double totalExpense = transactions.stream()
            .filter(tx -> tx.amountOut() != null && tx.amountOut() > 0)
            .mapToDouble(TransactionDto::amountOut)
            .sum();

        log.info("Financial Health Calculation: totalIncome={}, totalExpense={}", totalIncome, totalExpense);

        // Simple health score: (income - expense) / income * 100
        int healthScore;
        if (totalIncome > 0) {
            healthScore = (int) (((totalIncome - totalExpense) / totalIncome) * 100);
            log.info("✓ Calculated health score: {} (based on income-expense ratio)", healthScore);
        } else {
            healthScore = 50;
            log.warn("⚠ No income found, using default health score: 50");
        }

        healthScore = Math.max(0, Math.min(100, healthScore));

        String severity = healthScore >= 70 ? "LOW" : healthScore >= 40 ? "MEDIUM" : "HIGH";
        String message = healthScore >= 70
            ? "Tài chính của bạn khá ổn định"
            : healthScore >= 40
            ? "Tài chính cần chú ý hơn"
            : "Cần cải thiện tình hình tài chính";

        return AnalysisResult.builder()
            .success(true)
            .analysisType(AnalysisRequest.AnalysisType.FINANCIAL_HEALTH_SCORE)
            .healthScore(healthScore)
            .insight(String.format("Điểm sức khỏe tài chính: %d/100", healthScore))
            .insights(List.of(AnalysisResult.Insight.builder()
                .message(message)
                .severity(severity)
                .build()))
            .engineType(AnalysisResult.EngineType.RULE_BASED)
            .confidence(0.8)
            .build();
    }

    /**
     * Analyze debt impact (placeholder).
     */
    private AnalysisResult analyzeDebtImpact(AnalysisRequest request) {
        return AnalysisResult.builder()
            .success(true)
            .analysisType(AnalysisRequest.AnalysisType.DEBT_IMPACT)
            .insight("Phân tích tác động nợ chưa khả dụng")
            .engineType(AnalysisResult.EngineType.RULE_BASED)
            .confidence(0.5)
            .build();
    }

    /**
     * Fetch transactions for analysis with optional date filtering.
     */
    private List<TransactionDto> fetchTransactions(String userId, String periodType) {
        try {
            // Convert periodType to date range
            DateRange dateRange = convertPeriodToDateRange(periodType);

            var response = financeServiceClient.getTransactionHistory(
                userId,
                dateRange.startDate(),
                dateRange.endDate(),
                null, null, null,
                "transactionDate", "DESC", 0, 1000,
                null  // bankAccountIds - personal context
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return response.getBody().content();
            }
        } catch (Exception e) {
            log.error("Error fetching transactions: {}", e.getMessage());
        }
        return List.of();
    }

    /**
     * Fetch transactions with AnalysisRequest (supports custom date range).
     */
    private List<TransactionDto> fetchTransactions(AnalysisRequest request) {
        // If custom date range is provided, use it directly
        if (request.getStartDate() != null && request.getEndDate() != null) {
            log.info("Fetching transactions for userId={}, dateRange=[{} to {}]",
                     request.getUserId(), request.getStartDate(), request.getEndDate());
            try {
                var response = financeServiceClient.getTransactionHistory(
                    request.getUserId(),
                    request.getStartDate(),
                    request.getEndDate(),
                    null, null, null,
                    "transactionDate", "DESC", 0, 1000,
                    null  // bankAccountIds - personal context
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List<TransactionDto> transactions = response.getBody().content();
                    log.info("✓ Fetched {} transactions for custom date range [{} to {}]",
                             transactions.size(), request.getStartDate(), request.getEndDate());

                    // Log income/expense breakdown
                    long incomeCount = transactions.stream()
                        .filter(tx -> tx.amountIn() != null && tx.amountIn() > 0)
                        .count();
                    long expenseCount = transactions.stream()
                        .filter(tx -> tx.amountOut() != null && tx.amountOut() > 0)
                        .count();
                    log.info("  → {} income transactions, {} expense transactions", incomeCount, expenseCount);

                    return transactions;
                }
            } catch (Exception e) {
                log.error("Error fetching transactions with custom date range: {}", e.getMessage());
            }
            log.warn("⚠ No transactions found for date range [{} to {}]",
                     request.getStartDate(), request.getEndDate());
            return List.of();
        }

        // Otherwise, fall back to periodType-based fetching
        log.info("Fetching transactions for userId={}, periodType={}",
                 request.getUserId(), request.getPeriodType());
        List<TransactionDto> transactions = fetchTransactions(request.getUserId(), request.getPeriodType());
        log.info("✓ Fetched {} transactions for periodType={}", transactions.size(), request.getPeriodType());
        return transactions;
    }

    /**
     * Convert period type to date range.
     */
    private DateRange convertPeriodToDateRange(String periodType) {
        if (periodType == null) {
            return new DateRange(null, null); // No filtering
        }

        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate startDate;
        java.time.LocalDate endDate = today;

        switch (periodType.toUpperCase()) {
            case "THIS_MONTH":
                startDate = today.withDayOfMonth(1);
                break;
            case "LAST_MONTH":
                startDate = today.minusMonths(1).withDayOfMonth(1);
                endDate = today.minusMonths(1).withDayOfMonth(
                    today.minusMonths(1).lengthOfMonth()
                );
                break;
            case "THIS_YEAR":
                startDate = today.withDayOfYear(1);
                break;
            case "LAST_YEAR":
                startDate = today.minusYears(1).withDayOfYear(1);
                endDate = today.minusYears(1).withDayOfYear(
                    today.minusYears(1).lengthOfYear()
                );
                break;
            default:
                // Unknown period, no filtering
                return new DateRange(null, null);
        }

        java.time.format.DateTimeFormatter formatter =
            java.time.format.DateTimeFormatter.ISO_LOCAL_DATE;

        return new DateRange(
            startDate.format(formatter),
            endDate.format(formatter)
        );
    }

    private record DateRange(String startDate, String endDate) {}
}
