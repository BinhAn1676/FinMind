package com.finance.aiservice.engine;

import com.finance.aiservice.client.FinanceServiceClient;
import com.finance.aiservice.dto.TransactionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Forecasting engine for predictive analytics.
 *
 * Uses linear regression to predict:
 * - Next month expenses
 * - Future balance
 * - Cash flow warnings
 *
 * Provides explainable AI reasoning for predictions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ForecastingEngine {

    private final FinanceServiceClient financeServiceClient;

    @Qualifier("forecastingChatClient")
    private final ChatClient forecastingChatClient;

    /**
     * Generate financial forecast for next period.
     */
    public ForecastResult forecast(String userId, String forecastPeriod) {
        log.info("Generating forecast for userId={}, period={}", userId, forecastPeriod);

        try {
            // Fetch historical data (last 3-6 months)
            List<MonthlyData> historicalData = fetchHistoricalMonthlyData(userId, 6);

            if (historicalData.size() < 3) {
                return ForecastResult.error("Không đủ dữ liệu lịch sử để dự đoán (cần ít nhất 3 tháng)");
            }

            // Predict overall expense for next month
            ForecastResult.Prediction overallPrediction = predictNextMonthExpense(historicalData);

            // Predict per-category expenses
            List<ForecastResult.Prediction> categoryPredictions = predictCategoryExpenses(userId, historicalData);

            // Calculate projected balance
            Double projectedBalance = calculateProjectedBalance(historicalData, overallPrediction);

            // Check for cash flow warnings
            ForecastResult.CashFlowWarning warning = checkCashFlowWarning(historicalData, overallPrediction, projectedBalance);

            // Generate explainable AI reasoning
            String explanation = generateExplanation(overallPrediction, categoryPredictions, warning);

            return ForecastResult.builder()
                .success(true)
                .forecastPeriod(forecastPeriod != null ? forecastPeriod : "NEXT_MONTH")
                .overallPrediction(overallPrediction)
                .categoryPredictions(categoryPredictions)
                .projectedBalance(projectedBalance)
                .cashFlowWarning(warning)
                .explanation(explanation)
                .confidence(overallPrediction.getConfidence())
                .build();

        } catch (Exception e) {
            log.error("Error generating forecast: {}", e.getMessage(), e);
            return ForecastResult.error("Lỗi dự đoán: " + e.getMessage());
        }
    }

    /**
     * Fetch historical monthly spending data.
     */
    private List<MonthlyData> fetchHistoricalMonthlyData(String userId, int months) {
        List<MonthlyData> monthlyData = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = months - 1; i >= 0; i--) {
            YearMonth month = YearMonth.from(today.minusMonths(i));
            LocalDate startDate = month.atDay(1);
            LocalDate endDate = month.atEndOfMonth();

            try {
                String startDateStr = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
                String endDateStr = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
                log.debug("Fetching transactions for month {} (userId={}, range={} to {})",
                    month, userId, startDateStr, endDateStr);

                var response = financeServiceClient.getTransactionHistory(
                    userId,
                    startDateStr,
                    endDateStr,
                    null, null, null,
                    "transactionDate", "DESC", 0, 1000,
                    null  // bankAccountIds - personal context
                );

                if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                    List<TransactionDto> transactions = response.getBody().content();

                    double totalIncome = transactions.stream()
                        .filter(tx -> tx.amountIn() != null && tx.amountIn() > 0)
                        .mapToDouble(TransactionDto::amountIn)
                        .sum();

                    double totalExpense = transactions.stream()
                        .filter(tx -> tx.amountOut() != null && tx.amountOut() > 0)
                        .mapToDouble(TransactionDto::amountOut)
                        .sum();

                    // Group by category
                    Map<String, Double> categoryExpenses = transactions.stream()
                        .filter(tx -> tx.amountOut() != null && tx.amountOut() > 0)
                        .collect(Collectors.groupingBy(
                            tx -> tx.category() != null ? tx.category() : "Khác",
                            Collectors.summingDouble(TransactionDto::amountOut)
                        ));

                    monthlyData.add(new MonthlyData(
                        month,
                        totalIncome,
                        totalExpense,
                        categoryExpenses
                    ));
                }
            } catch (Exception e) {
                log.error("Failed to fetch data for month {} (userId={}): {}", month, userId, e.getMessage(), e);
            }
        }

        log.info("Fetched {} months of historical data for userId={} (requested {} months)",
            monthlyData.size(), userId, months);
        return monthlyData;
    }

    /**
     * Predict next month overall expense using linear regression.
     */
    private ForecastResult.Prediction predictNextMonthExpense(List<MonthlyData> historicalData) {
        // Extract expense values for regression
        List<Double> expenses = historicalData.stream()
            .map(MonthlyData::totalExpense)
            .collect(Collectors.toList());

        // Simple linear regression: y = mx + b
        LinearRegressionResult regression = calculateLinearRegression(expenses);

        // Predict next point
        double predictedExpense = regression.slope * expenses.size() + regression.intercept;
        predictedExpense = Math.max(0, predictedExpense); // Non-negative

        // Calculate current month expense
        double currentExpense = historicalData.get(historicalData.size() - 1).totalExpense();

        // Calculate change percentage
        double changePercent = currentExpense > 0
            ? ((predictedExpense - currentExpense) / currentExpense) * 100
            : 0;

        // Determine trend
        String trend = Math.abs(changePercent) < 5 ? "STABLE"
            : changePercent > 0 ? "INCREASING"
            : "DECREASING";

        // Calculate confidence based on R-squared
        double confidence = regression.rSquared;

        return ForecastResult.Prediction.builder()
            .category("TOTAL")
            .predictedAmount(predictedExpense)
            .currentAmount(currentExpense)
            .changePercent(changePercent)
            .trend(trend)
            .confidence(confidence)
            .build();
    }

    /**
     * Predict per-category expenses.
     */
    private List<ForecastResult.Prediction> predictCategoryExpenses(String userId, List<MonthlyData> historicalData) {
        // Find all unique categories across all months
        Set<String> allCategories = historicalData.stream()
            .flatMap(data -> data.categoryExpenses().keySet().stream())
            .collect(Collectors.toSet());

        List<ForecastResult.Prediction> predictions = new ArrayList<>();

        for (String category : allCategories) {
            // Extract category expenses for each month
            List<Double> categoryValues = historicalData.stream()
                .map(data -> data.categoryExpenses().getOrDefault(category, 0.0))
                .collect(Collectors.toList());

            // Skip if not enough data
            if (categoryValues.stream().filter(v -> v > 0).count() < 2) {
                continue;
            }

            // Linear regression for this category
            LinearRegressionResult regression = calculateLinearRegression(categoryValues);
            double predictedAmount = regression.slope * categoryValues.size() + regression.intercept;
            predictedAmount = Math.max(0, predictedAmount);

            double currentAmount = categoryValues.get(categoryValues.size() - 1);
            double changePercent = currentAmount > 0
                ? ((predictedAmount - currentAmount) / currentAmount) * 100
                : 0;

            String trend = Math.abs(changePercent) < 5 ? "STABLE"
                : changePercent > 0 ? "INCREASING"
                : "DECREASING";

            predictions.add(ForecastResult.Prediction.builder()
                .category(category)
                .predictedAmount(predictedAmount)
                .currentAmount(currentAmount)
                .changePercent(changePercent)
                .trend(trend)
                .confidence(regression.rSquared)
                .build());
        }

        // Sort by predicted amount descending (top spending categories first)
        predictions.sort((a, b) -> Double.compare(b.getPredictedAmount(), a.getPredictedAmount()));

        return predictions;
    }

    /**
     * Calculate projected balance.
     */
    private Double calculateProjectedBalance(List<MonthlyData> historicalData, ForecastResult.Prediction overallPrediction) {
        // Estimate average income from historical data
        double avgIncome = historicalData.stream()
            .mapToDouble(MonthlyData::totalIncome)
            .average()
            .orElse(0);

        // Projected balance = predicted income - predicted expense
        return avgIncome - overallPrediction.getPredictedAmount();
    }

    /**
     * Check for cash flow warnings.
     */
    private ForecastResult.CashFlowWarning checkCashFlowWarning(
        List<MonthlyData> historicalData,
        ForecastResult.Prediction overallPrediction,
        Double projectedBalance
    ) {
        if (projectedBalance >= 0) {
            return null; // No warning
        }

        double deficit = Math.abs(projectedBalance);
        String severity;
        String message;
        String recommendation;

        if (deficit < 1000000) {
            severity = "LOW";
            message = String.format("Dự kiến thâm hụt nhỏ: %.0f ₫ tháng tới", deficit);
            recommendation = "Theo dõi chi tiêu trong tuần tới";
        } else if (deficit < 5000000) {
            severity = "MEDIUM";
            message = String.format("Cảnh báo: Dự kiến thâm hụt %.0f ₫ tháng tới", deficit);
            recommendation = "Xem xét cắt giảm chi tiêu không thiết yếu";
        } else {
            severity = "HIGH";
            message = String.format("⚠️ Cảnh báo nghiêm trọng: Dự kiến thâm hụt %.0f ₫ tháng tới", deficit);
            recommendation = "Cần hành động ngay: Giảm chi tiêu hoặc tăng thu nhập";
        }

        return ForecastResult.CashFlowWarning.builder()
            .severity(severity)
            .message(message)
            .projectedDeficit(deficit)
            .recommendation(recommendation)
            .build();
    }

    /**
     * Generate explainable AI reasoning for forecast.
     */
    private String generateExplanation(
        ForecastResult.Prediction overallPrediction,
        List<ForecastResult.Prediction> categoryPredictions,
        ForecastResult.CashFlowWarning warning
    ) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Dựa trên phân tích xu hướng chi tiêu:\n\n");
        prompt.append(String.format("- Dự đoán chi tiêu tháng tới: %.0f ₫\n", overallPrediction.getPredictedAmount()));
        prompt.append(String.format("- Chi tiêu tháng hiện tại: %.0f ₫\n", overallPrediction.getCurrentAmount()));
        prompt.append(String.format("- Xu hướng: %s (%.1f%%)\n", overallPrediction.getTrend(), overallPrediction.getChangePercent()));
        prompt.append(String.format("- Độ tin cậy: %.0f%%\n\n", overallPrediction.getConfidence() * 100));

        if (!categoryPredictions.isEmpty()) {
            prompt.append("Các danh mục có xu hướng tăng:\n");
            categoryPredictions.stream()
                .filter(p -> "INCREASING".equals(p.getTrend()))
                .limit(3)
                .forEach(p -> prompt.append(String.format("- %s: +%.1f%%\n", p.getCategory(), p.getChangePercent())));
        }

        if (warning != null) {
            prompt.append(String.format("\n%s\n%s\n", warning.getMessage(), warning.getRecommendation()));
        }

        prompt.append("\nGiải thích ngắn gọn (2-3 câu) nguyên nhân và khuyến nghị:");

        try {
            return forecastingChatClient
                .prompt()
                .user(prompt.toString())
                .call()
                .content();
        } catch (Exception e) {
            log.warn("Failed to generate AI explanation: {}", e.getMessage());
            return "Dự đoán dựa trên xu hướng chi tiêu của bạn trong " + overallPrediction.getTrend().toLowerCase() + ".";
        }
    }

    /**
     * Calculate linear regression for time series data.
     */
    private LinearRegressionResult calculateLinearRegression(List<Double> values) {
        int n = values.size();
        if (n < 2) {
            return new LinearRegressionResult(0, values.get(0), 0);
        }

        // Calculate means
        double sumX = 0, sumY = 0;
        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += values.get(i);
        }
        double meanX = sumX / n;
        double meanY = sumY / n;

        // Calculate slope and intercept
        double numerator = 0, denominator = 0;
        for (int i = 0; i < n; i++) {
            double dx = i - meanX;
            double dy = values.get(i) - meanY;
            numerator += dx * dy;
            denominator += dx * dx;
        }

        double slope = denominator != 0 ? numerator / denominator : 0;
        double intercept = meanY - slope * meanX;

        // Calculate R-squared (coefficient of determination)
        double ssRes = 0, ssTot = 0;
        for (int i = 0; i < n; i++) {
            double predicted = slope * i + intercept;
            double actual = values.get(i);
            ssRes += Math.pow(actual - predicted, 2);
            ssTot += Math.pow(actual - meanY, 2);
        }

        double rSquared = ssTot != 0 ? 1 - (ssRes / ssTot) : 0;
        rSquared = Math.max(0, Math.min(1, rSquared)); // Clamp to [0, 1]

        return new LinearRegressionResult(slope, intercept, rSquared);
    }

    /**
     * Monthly financial data.
     */
    private record MonthlyData(
        YearMonth month,
        double totalIncome,
        double totalExpense,
        Map<String, Double> categoryExpenses
    ) {}

    /**
     * Linear regression result.
     */
    private record LinearRegressionResult(
        double slope,
        double intercept,
        double rSquared  // Coefficient of determination (0-1)
    ) {}
}
