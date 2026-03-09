package com.finance.aiservice.service;

import com.finance.aiservice.client.FinanceServiceClient;
import com.finance.aiservice.dto.AnalyticsDashboardResponse.*;
import com.finance.aiservice.dto.TransactionDto;
import com.finance.aiservice.dto.TransactionPage;
import com.finance.aiservice.dto.TransactionSummaryDto;
import com.finance.aiservice.engine.AnalysisRequest;
import com.finance.aiservice.engine.AnalysisResult;
import com.finance.aiservice.engine.ForecastResult;
import com.finance.aiservice.engine.ForecastingEngine;
import com.finance.aiservice.service.HybridEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Analytics Dashboard Service
 *
 * Provides structured data for AI-powered analytics dashboard:
 * - Financial health scoring
 * - Spending structure analysis
 * - Anomaly detection
 * - Budget forecasting
 * - Pattern recognition
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsDashboardService {

    private final HybridEngineService hybridEngineService;
    private final ForecastingEngine forecastingEngine;
    private final FinanceServiceClient financeServiceClient;

    /**
     * Get financial health score for dashboard
     * @param userId User ID
     * @param year Optional year (defaults to current year)
     * @param month Optional month 1-12 (defaults to current month)
     */
    public HealthScore getFinancialHealth(String userId, Integer year, Integer month) {
        // Default to current month if not specified
        LocalDateTime targetDate = LocalDateTime.now();
        if (year != null && month != null) {
            targetDate = LocalDateTime.of(year, month, 1, 0, 0);
            log.info("📅 User selected custom month: year={}, month={}", year, month);
        } else {
            log.info("📅 Using current month: year={}, month={}",
                     targetDate.getYear(), targetDate.getMonthValue());
        }

        // Calculate date range for the selected month
        String startDate = targetDate.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String endDate = targetDate.withDayOfMonth(targetDate.toLocalDate().lengthOfMonth())
                                   .format(DateTimeFormatter.ISO_LOCAL_DATE);

        log.info("📊 Calculating health score for date range: [{}] to [{}]", startDate, endDate);

        AnalysisRequest request = AnalysisRequest.builder()
            .analysisType(AnalysisRequest.AnalysisType.FINANCIAL_HEALTH_SCORE)
            .userId(userId)
            .startDate(startDate)
            .endDate(endDate)
            .build();

        AnalysisResult result = hybridEngineService.analyze(request);

        if (!result.isSuccess() || result.getHealthScore() == null) {
            log.warn("⚠ Health score calculation failed or returned null");
            return HealthScore.builder()
                .score(0)
                .grade("N/A")
                .status("Không đủ dữ liệu")
                .message("Cần thêm giao dịch để phân tích")
                .strengths(List.of())
                .concerns(List.of())
                .build();
        }

        // Use the calculated health score directly from AnalysisResult
        Integer score = result.getHealthScore();
        log.info("✓ Using health score from AnalysisResult: {}", score);

        // Get primary insight message if available
        String message = result.getInsight() != null ? result.getInsight() : calculateHealthMessage(score);

        return HealthScore.builder()
            .score(score)
            .grade(calculateGrade(score))
            .status(calculateStatus(score))
            .message(message)
            .strengths(extractPositivePoints(result))
            .concerns(extractConcerns(result))
            .aiNarrative(result.getAiNarrative())
            .aiEnhanced(result.isAiEnhanced())
            .build();
    }

    /**
     * Generate health message based on score if not provided by engine
     */
    private String calculateHealthMessage(Integer score) {
        if (score >= 80) return "Tài chính của bạn rất tốt";
        if (score >= 60) return "Tài chính của bạn khá ổn định";
        if (score >= 40) return "Tài chính cần chú ý hơn";
        return "Cần cải thiện tình hình tài chính";
    }

    /**
     * Get spending structure for chart
     * Uses 50-30-20 rule based on ACTUAL INCOME:
     * - 50% of income → Essential needs
     * - 30% of income → Entertainment/Wants
     * - 20% of income → Savings/Investment
     * @param userId User ID
     * @param year Optional year (defaults to current year)
     * @param month Optional month 1-12 (defaults to current month)
     */
    public SpendingStructure getSpendingStructure(String userId, Integer year, Integer month) {
        // Default to current month if not specified
        LocalDateTime targetDate = LocalDateTime.now();
        if (year != null && month != null) {
            targetDate = LocalDateTime.of(year, month, 1, 0, 0);
        }

        log.info("Generating spending structure for userId={}, year={}, month={}",
                 userId, targetDate.getYear(), targetDate.getMonthValue());

        // Calculate date range for the target month
        String startDate = targetDate.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String endDate = targetDate.withDayOfMonth(targetDate.toLocalDate().lengthOfMonth()).format(DateTimeFormatter.ISO_LOCAL_DATE);

        // Fetch income and expense summary for the month
        TransactionSummaryDto summary = financeServiceClient
            .getSpendingSummary(userId, startDate, endDate, null)
            .getBody();

        if (summary == null) {
            log.warn("No summary data found for userId={} in period {}-{}", userId, startDate, endDate);
            return SpendingStructure.builder()
                .categories(new ArrayList<>())
                .totalIncome(0.0)
                .totalExpense(0.0)
                .overallStatus("NO_DATA")
                .recommendation("Chưa có dữ liệu giao dịch trong tháng này")
                .build();
        }

        Double totalIncome = summary.totalIncome() != null ? summary.totalIncome() : 0.0;
        Double totalExpense = summary.totalExpense() != null ? summary.totalExpense() : 0.0;

        log.info("Month summary for userId={}: income={}, expense={}", userId, totalIncome, totalExpense);

        // If no income data, cannot calculate ideal benchmarks
        if (totalIncome <= 0) {
            log.warn("No income data for userId={}, cannot calculate ideal spending benchmarks", userId);
            return SpendingStructure.builder()
                .categories(new ArrayList<>())
                .totalIncome(totalIncome)
                .totalExpense(totalExpense)
                .overallStatus("NO_DATA")
                .recommendation("Chưa có dữ liệu thu nhập trong tháng này. Quy tắc 50-30-20 cần có thu nhập để tính toán mốc lý tưởng.")
                .build();
        }

        // Fetch actual expense transactions to categorize spending
        TransactionPage transactionPage = financeServiceClient
            .getTransactionHistory(userId, startDate, endDate, null, null, "expense",
                                  "transactionDate", "DESC", 0, 1000, null)
            .getBody();

        // Group transactions by category
        Map<String, Double> categoryTotals = new java.util.HashMap<>();
        if (transactionPage != null && transactionPage.content() != null && !transactionPage.content().isEmpty()) {
            categoryTotals = transactionPage.content().stream()
                .filter(t -> t.category() != null && !t.category().isEmpty())
                .collect(Collectors.groupingBy(
                    TransactionDto::category,
                    Collectors.summingDouble(t -> t.amountOut() != null ? t.amountOut() : 0.0)
                ));
        }

        // Map categories to spending types
        Map<String, String> categoryMapping = Map.ofEntries(
            // Nhu cầu thiết yếu (Essential needs) - 50% of income
            Map.entry("Tiền nhà", "Nhu cầu thiết yếu"),
            Map.entry("Ăn uống", "Nhu cầu thiết yếu"),
            Map.entry("Hóa đơn", "Nhu cầu thiết yếu"),
            Map.entry("Giao thông", "Nhu cầu thiết yếu"),
            Map.entry("Y tế", "Nhu cầu thiết yếu"),
            Map.entry("Sức khỏe", "Nhu cầu thiết yếu"),
            Map.entry("Giáo dục", "Nhu cầu thiết yếu"),
            Map.entry("Điện nước", "Nhu cầu thiết yếu"),
            Map.entry("Internet", "Nhu cầu thiết yếu"),

            // Hưởng thụ / Giải trí (Entertainment/Wants) - 30% of income
            Map.entry("Mua sắm", "Hưởng thụ / Giải trí"),
            Map.entry("Giải trí", "Hưởng thụ / Giải trí"),
            Map.entry("Du lịch", "Hưởng thụ / Giải trí"),
            Map.entry("Thể thao", "Hưởng thụ / Giải trí"),
            Map.entry("Làm đẹp", "Hưởng thụ / Giải trí"),
            Map.entry("Thời trang", "Hưởng thụ / Giải trí"),
            Map.entry("Cafe", "Hưởng thụ / Giải trí"),

            // Tiết kiệm / Đầu tư (Savings/Investment) - 20% of income
            Map.entry("Đầu tư", "Tiết kiệm / Đầu tư"),
            Map.entry("Tiết kiệm", "Tiết kiệm / Đầu tư"),
            Map.entry("Bảo hiểm", "Tiết kiệm / Đầu tư"),

            // Không xác định -> Default to Essential
            Map.entry("Không xác định", "Nhu cầu thiết yếu"),
            Map.entry("Chi tiêu khác", "Nhu cầu thiết yếu"),
            Map.entry("Khác", "Nhu cầu thiết yếu")
        );

        // Ideal percentages based on 50-30-20 rule (% of INCOME, not expense)
        Map<String, Double> idealPercentMapping = Map.of(
            "Nhu cầu thiết yếu", 50.0,
            "Hưởng thụ / Giải trí", 30.0,
            "Tiết kiệm / Đầu tư", 20.0
        );

        // Group by spending type
        Map<String, Double> spendingTypeTotals = new java.util.HashMap<>();
        categoryTotals.forEach((category, amount) -> {
            String spendingType = categoryMapping.getOrDefault(category, "Nhu cầu thiết yếu");
            spendingTypeTotals.merge(spendingType, amount, Double::sum);
        });

        // Create spending categories with income-based ideal amounts
        List<SpendingCategory> categories = idealPercentMapping.entrySet().stream()
            .map(entry -> {
                String name = entry.getKey();
                Double idealPercent = entry.getValue();
                Double idealAmount = (idealPercent / 100.0) * totalIncome;  // Ideal based on INCOME
                Double actualAmount = spendingTypeTotals.getOrDefault(name, 0.0);
                Double actualPercent = totalIncome > 0 ? (actualAmount / totalIncome) * 100 : 0;
                String status = determineStatusByIncome(actualAmount, idealAmount, actualPercent, idealPercent);

                return SpendingCategory.builder()
                    .name(name)
                    .actualPercent(Math.round(actualPercent * 10) / 10.0)
                    .idealPercent(idealPercent)
                    .amount(actualAmount)
                    .idealAmount((double) Math.round(idealAmount))
                    .status(status)
                    .build();
            })
            .collect(Collectors.toList());

        String recommendation = generateRecommendationByIncome(categories, totalIncome, totalExpense);

        return SpendingStructure.builder()
            .categories(categories)
            .totalIncome(totalIncome)
            .totalExpense(totalExpense)
            .overallStatus(determineOverallStatus(categories))
            .recommendation(recommendation)
            .build();
    }

    /**
     * Determine status based on income-based ideal amounts (NEW - income-aware)
     * @param actualAmount Actual spending amount
     * @param idealAmount Ideal spending amount based on income
     * @param actualPercent Actual spending as % of income
     * @param idealPercent Ideal spending as % of income
     */
    private String determineStatusByIncome(Double actualAmount, Double idealAmount, Double actualPercent, Double idealPercent) {
        double percentDiff = actualPercent - idealPercent;

        // For savings/investment: spending LESS is good (means more saved)
        if (idealPercent == 20.0) {  // Tiết kiệm / Đầu tư
            if (percentDiff >= 0) return "GOOD";        // Saved more than ideal
            if (percentDiff >= -5) return "WARNING";    // Slightly under
            return "CRITICAL";                           // Way under ideal
        }

        // For essential needs and entertainment: spending MORE is bad
        if (percentDiff <= 5) return "GOOD";            // Within 5% of ideal
        if (percentDiff <= 10) return "WARNING";        // 5-10% over ideal
        return "CRITICAL";                               // >10% over ideal
    }

    /**
     * OLD method - kept for backward compatibility
     */
    private String determineStatus(Double actual, Double ideal) {
        double diff = Math.abs(actual - ideal);
        if (diff <= 5) return "GOOD";
        if (diff <= 15) return "WARNING";
        return "ALERT";
    }

    /**
     * Generate recommendation based on income and expense totals (NEW)
     */
    private String generateRecommendationByIncome(List<SpendingCategory> categories, Double totalIncome, Double totalExpense) {
        long criticals = categories.stream().filter(c -> "CRITICAL".equals(c.getStatus())).count();
        long warnings = categories.stream().filter(c -> "WARNING".equals(c.getStatus())).count();

        double savingsRate = totalIncome > 0 ? ((totalIncome - totalExpense) / totalIncome) * 100 : 0;

        if (criticals > 0) {
            return String.format("⚠️ Cần điều chỉnh cơ cấu chi tiêu ngay. Thu nhập: %,.0f₫, Chi tiêu: %,.0f₫, Tiết kiệm: %.1f%%",
                totalIncome, totalExpense, savingsRate);
        } else if (warnings > 0) {
            return String.format("Chi tiêu tương đối hợp lý, khuyến nghị tối ưu thêm. Tỷ lệ tiết kiệm: %.1f%%", savingsRate);
        }
        return String.format("✅ Chi tiêu rất tốt theo quy tắc 50-30-20. Tỷ lệ tiết kiệm: %.1f%%", savingsRate);
    }

    /**
     * OLD method - kept for backward compatibility
     */
    private String generateRecommendation(List<SpendingCategory> categories) {
        long alerts = categories.stream().filter(c -> "ALERT".equals(c.getStatus())).count();
        long warnings = categories.stream().filter(c -> "WARNING".equals(c.getStatus())).count();

        if (alerts > 0) {
            return "Cần điều chỉnh cơ cấu chi tiêu để cải thiện tài chính";
        } else if (warnings > 0) {
            return "Chi tiêu tương đối hợp lý, khuyến nghị tối ưu thêm";
        }
        return "Chi tiêu rất tốt, duy trì thói quen này";
    }

    /**
     * Get anomaly alerts
     * @param userId User ID
     * @param year Optional year (defaults to current year)
     * @param month Optional month 1-12 (defaults to current month)
     */
    public List<AnomalyAlert> getAnomalies(String userId, Integer year, Integer month) {
        // Default to current month if not specified
        LocalDateTime targetDate = LocalDateTime.now();
        if (year != null && month != null) {
            targetDate = LocalDateTime.of(year, month, 1, 0, 0);
        }

        log.info("Detecting anomalies for userId={}, year={}, month={}",
                 userId, targetDate.getYear(), targetDate.getMonthValue());

        // Create final variable for lambda
        final String formattedDate = targetDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        // Calculate date range for the selected month
        String startDate = targetDate.withDayOfMonth(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String endDate = targetDate.withDayOfMonth(targetDate.toLocalDate().lengthOfMonth())
                                   .format(DateTimeFormatter.ISO_LOCAL_DATE);

        AnalysisRequest request = AnalysisRequest.builder()
            .analysisType(AnalysisRequest.AnalysisType.SPENDING_ALERT)
            .userId(userId)
            .startDate(startDate)
            .endDate(endDate)
            .build();

        AnalysisResult result = hybridEngineService.analyze(request);

        log.info("Anomaly detection result: {} total insights found",
                 result.getInsights() != null ? result.getInsights().size() : 0);

        List<AnomalyAlert> anomalies = result.getInsights().stream()
            .filter(insight -> {
                // Accept HIGH severity as anomalies (spending alerts from RuleBasedEngine)
                boolean isAnomaly = "HIGH".equals(insight.getSeverity()) || "ALERT".equals(insight.getSeverity());
                if (!isAnomaly) {
                    log.debug("Filtering out insight with severity: {} (only HIGH or ALERT are considered anomalies)",
                             insight.getSeverity());
                }
                return isAnomaly;
            })
            .map(insight -> AnomalyAlert.builder()
                .transactionId("N/A")
                .description(extractDescription(insight.getMessage()))
                .amount(extractAmount(insight.getMessage()))
                .date(formattedDate)
                .severity("HIGH")
                .reason(insight.getMessage())
                .recommendation(insight.getRecommendation())
                .build())
            .collect(Collectors.toList());

        log.info("✓ Returning {} anomaly alerts (filtered from {} total insights with HIGH/ALERT severity)",
                 anomalies.size(), result.getInsights().size());

        if (anomalies.isEmpty()) {
            log.warn("⚠ No anomalies found for date range [{} to {}]. Check if there are transactions with unusual patterns.",
                     startDate, endDate);
        }

        return anomalies;
    }

    /**
     * Get spending patterns
     */
    public List<SpendingPattern> getSpendingPatterns(String userId) {
        log.info("Analyzing spending patterns for userId={}", userId);

        List<SpendingPattern> patterns = new ArrayList<>();

        patterns.add(SpendingPattern.builder()
            .type("TIME")
            .pattern("20h - 22h")
            .description("Không gửi tiền nhắc nhở [Shopping Online]")
            .recommendations(List.of("Tránh mua sắm trực tuyến buổi tối"))
            .build());

        patterns.add(SpendingPattern.builder()
            .type("DAY")
            .pattern("Thứ Bảy")
            .description("Nghỉ chi tiêu cảm xúc giữa tuần gặp với bạn bè hàng ngày")
            .recommendations(List.of("Lập kế hoạch chi tiêu cuối tuần"))
            .build());

        patterns.add(SpendingPattern.builder()
            .type("BEHAVIOR")
            .pattern("Impulse Buy")
            .description("Ai phải mua 4 gam cách để tránh mua không")
            .recommendations(List.of("Áp dụng quy tắc 24 giờ trước khi mua"))
            .build());

        return patterns;
    }

    /**
     * Get budget forecast with timeline
     */
    public BudgetForecast getBudgetForecast(String userId) {
        log.info("Generating budget forecast for userId={}", userId);

        ForecastResult forecast = forecastingEngine.forecast(userId, "NEXT_MONTH");

        if (!forecast.isSuccess()) {
            return BudgetForecast.builder()
                .forecastPeriod("30 ngày tới")
                .projectedBalance(0.0)
                .projectedExpense(0.0)
                .trend("STABLE")
                .confidence(0.0)
                .timeline(List.of())
                .warning(null)
                .build();
        }

        // Generate weekly timeline
        List<ForecastPoint> timeline = generateWeeklyTimeline(forecast);

        // Convert cash flow warning
        CashFlowWarning warning = null;
        if (forecast.getCashFlowWarning() != null) {
            warning = CashFlowWarning.builder()
                .severity(forecast.getCashFlowWarning().getSeverity())
                .message(forecast.getCashFlowWarning().getMessage())
                .projectedDeficit(forecast.getCashFlowWarning().getProjectedDeficit())
                .recommendation(forecast.getCashFlowWarning().getRecommendation())
                .build();
        }

        return BudgetForecast.builder()
            .forecastPeriod("30 ngày tới")
            .projectedBalance(forecast.getProjectedBalance())
            .projectedExpense(forecast.getOverallPrediction().getPredictedAmount())
            .trend(forecast.getOverallPrediction().getTrend())
            .confidence(forecast.getConfidence() * 100)
            .timeline(timeline)
            .warning(warning)
            .build();
    }

    /**
     * Get discipline recommendations
     */
    public List<DisciplineRecommendation> getDisciplineRecommendations(String userId) {
        log.info("Generating discipline recommendations for userId={}", userId);

        AnalysisRequest request = AnalysisRequest.builder()
            .analysisType(AnalysisRequest.AnalysisType.SAVINGS_RECOMMENDATION)
            .userId(userId)
            .periodType("THIS_MONTH")
            .build();

        AnalysisResult result = hybridEngineService.analyze(request);

        // Handle null insights list
        if (result.getInsights() == null || result.getInsights().isEmpty()) {
            log.warn("No discipline recommendations found for userId={}", userId);
            return Collections.emptyList();
        }

        return result.getInsights().stream()
            .map(insight -> DisciplineRecommendation.builder()
                .category(extractCategory(insight.getMessage()))
                .currentAmount(10000000.0)
                .targetAmount(8000000.0)
                .savingsPotential(2000000.0)
                .priority(calculatePriority(insight.getSeverity()))
                .build())
            .collect(Collectors.toList());
    }

    /**
     * Get complete dashboard data
     */
    public CompleteDashboard getCompleteDashboard(String userId) {
        log.info("Generating complete analytics dashboard for userId={}", userId);

        // Use null for year/month to default to current month
        return CompleteDashboard.builder()
            .healthScore(getFinancialHealth(userId, null, null))
            .spendingStructure(getSpendingStructure(userId, null, null))
            .anomalies(getAnomalies(userId, null, null))
            .patterns(getSpendingPatterns(userId))
            .budgetForecast(getBudgetForecast(userId))
            .recommendations(getDisciplineRecommendations(userId))
            .generatedAt(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .build();
    }

    // Helper methods

    private Integer extractScore(String message) {
        // Extract score from message like "Điểm sức khỏe tài chính: 85/100"
        if (message.contains("/100")) {
            try {
                String scoreStr = message.replaceAll(".*?(\\d+)/100.*", "$1");
                return Integer.parseInt(scoreStr);
            } catch (Exception e) {
                return 50;
            }
        }
        return 50;
    }

    private String calculateGrade(Integer score) {
        if (score >= 95) return "A+";
        if (score >= 90) return "A";
        if (score >= 85) return "A-";
        if (score >= 80) return "B+";
        if (score >= 75) return "B";
        if (score >= 70) return "B-";
        if (score >= 65) return "C+";
        if (score >= 60) return "C";
        return "C-";
    }

    private String calculateStatus(Integer score) {
        if (score >= 90) return "Xuất sắc";
        if (score >= 80) return "Tốt";
        if (score >= 70) return "Khá tốt";
        if (score >= 60) return "Trung bình";
        return "Cần cải thiện";
    }

    private List<String> extractPositivePoints(AnalysisResult result) {
        return result.getInsights().stream()
            .filter(i -> "INFO".equals(i.getSeverity()))
            .map(AnalysisResult.Insight::getMessage)
            .limit(3)
            .collect(Collectors.toList());
    }

    private List<String> extractConcerns(AnalysisResult result) {
        return result.getInsights().stream()
            .filter(i -> "WARNING".equals(i.getSeverity()) || "ALERT".equals(i.getSeverity()))
            .map(AnalysisResult.Insight::getMessage)
            .limit(3)
            .collect(Collectors.toList());
    }

    private String determineOverallStatus(List<SpendingCategory> categories) {
        long alerts = categories.stream().filter(c -> "ALERT".equals(c.getStatus())).count();
        long warnings = categories.stream().filter(c -> "WARNING".equals(c.getStatus())).count();

        if (alerts > 0) return "CRITICAL";
        if (warnings > 0) return "NEEDS_ATTENTION";
        return "HEALTHY";
    }

    private String extractDescription(String message) {
        // Extract description from alert message
        return message.split(":")[0].trim();
    }

    private Double extractAmount(String message) {
        // Extract amount from message
        try {
            String amountStr = message.replaceAll(".*?(\\d+[,.]?\\d*).*", "$1");
            return Double.parseDouble(amountStr.replace(",", ""));
        } catch (Exception e) {
            return 0.0;
        }
    }

    private String extractCategory(String message) {
        // Extract category from recommendation message
        if (message.contains("Mua sắm")) return "Cắt giảm Mua sắm";
        if (message.contains("tiết kiệm")) return "Tăng tiết kiệm";
        return "Tối ưu chi tiêu";
    }

    private Integer calculatePriority(String severity) {
        return switch (severity) {
            case "ALERT" -> 1;
            case "WARNING" -> 2;
            case "INFO" -> 3;
            default -> 5;
        };
    }

    private List<ForecastPoint> generateWeeklyTimeline(ForecastResult forecast) {
        List<ForecastPoint> timeline = new ArrayList<>();
        Double currentBalance = forecast.getProjectedBalance() + forecast.getOverallPrediction().getPredictedAmount();
        Double weeklyExpense = forecast.getOverallPrediction().getPredictedAmount() / 4;

        for (int week = 1; week <= 4; week++) {
            timeline.add(ForecastPoint.builder()
                .period("Tuần " + week)
                .balance(currentBalance - (weeklyExpense * week))
                .expense(weeklyExpense)
                .income(0.0)
                .build());
        }

        return timeline;
    }
}
