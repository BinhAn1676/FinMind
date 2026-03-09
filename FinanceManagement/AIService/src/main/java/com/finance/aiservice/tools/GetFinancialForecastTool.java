package com.finance.aiservice.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.finance.aiservice.context.RequestContext;
import com.finance.aiservice.context.ToolCallGuard;
import com.finance.aiservice.engine.ForecastResult;
import com.finance.aiservice.engine.ForecastingEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI Tool: Get Financial Forecast
 *
 * Provides predictive analytics and forecasting using linear regression.
 * Predicts next month spending, balance, and provides cash flow warnings.
 */
@Slf4j
@Component("getFinancialForecast")
@Description("""
    Get predictive financial forecasts and future projections.

    🚨 CALL THIS when user asks about:
    - "dự đoán chi tiêu tháng tới?" / "forecast next month spending?"
    - "tháng sau chi bao nhiêu?" / "how much will I spend next month?"
    - "cân đối tiền trong tương lai?" / "future balance?"
    - "cảnh báo dòng tiền?" / "cash flow warning?"
    - "xu hướng chi tiêu?" / "spending trend?"

    Returns AI-powered predictions with explainable reasoning.

    🛑🛑🛑 ABSOLUTELY CRITICAL - READ THIS CAREFULLY! 🛑🛑🛑

    After you call this function ONE TIME:
    1. You will receive a Response with a 'message' field
    2. The 'message' contains the COMPLETE forecast already formatted in Vietnamese
    3. IMMEDIATELY return that 'message' to the user - DO NOT MODIFY IT
    4. DO NOT call getFinancialForecast again - you already have the complete answer!
    5. DO NOT ask follow-up questions - the forecast is DONE!

    The forecast is READY TO SHOW after the FIRST call. Stop after ONE call!
    """)
@RequiredArgsConstructor
public class GetFinancialForecastTool implements Function<GetFinancialForecastTool.Request, GetFinancialForecastTool.Response> {

    private final ForecastingEngine forecastingEngine;

    @Override
    public Response apply(Request request) {
        // 🛑 GUARD: Prevent infinite loops with 3-strike approach
        int callCount = ToolCallGuard.incrementAndGetCallCount("getFinancialForecast");
        log.info("AI Tool called: getFinancialForecast for user {} (call #{}/3)", request.userId(), callCount);

        if (callCount == 2) {
            // Strike 2: Return cached result
            log.warn("⚠️ Strike 2: Returning cached forecast result to prevent infinite loop");
            Response cached = ToolCallGuard.getCachedResult("getFinancialForecast", Response.class);
            if (cached != null) {
                return cached;
            }
        } else if (callCount >= 3) {
            // Strike 3: Force stop
            log.error("🛑 Strike 3: STOPPING! AI called getFinancialForecast {} times", callCount);
            return Response.error("DỪNG! Đã cung cấp dự đoán rồi. Không gọi lại nữa!");
        }

        // 🔐 SECURITY: Override hallucinated userId with real one
        String userId = RequestContext.hasUserId()
            ? RequestContext.getUserId()
            : request.userId();

        if (RequestContext.hasUserId() && !userId.equals(request.userId())) {
            log.warn("⚠️ AI hallucinated userId='{}', overriding with '{}'",
                request.userId(), userId);
        }

        try {
            String forecastPeriod = request.forecastPeriod() != null ? request.forecastPeriod() : "NEXT_MONTH";

            ForecastResult forecast = forecastingEngine.forecast(userId, forecastPeriod);

            if (!forecast.isSuccess()) {
                // 🚨 Return user-friendly message instead of error
                // This prevents AI from retrying when there's insufficient data
                String userMessage = forecast.getErrorMessage();
                if (userMessage.contains("Không đủ dữ liệu")) {
                    userMessage = "📊 Để dự đoán chi tiêu tương lai, bạn cần có ít nhất 3 tháng dữ liệu giao dịch.\n\n" +
                                  "Hiện tại bạn chưa đủ dữ liệu lịch sử. Hãy tiếp tục ghi nhận giao dịch, " +
                                  "và tôi sẽ có thể đưa ra dự đoán chính xác hơn sau đó! 💡";
                }

                // Return as success with message (not error) so AI doesn't retry
                Response response = Response.success(userMessage);

                // Cache to prevent retries
                ToolCallGuard.cacheResult("getFinancialForecast", response);
                return response;
            }

            // Format forecast into natural language
            String formattedForecast = formatForecast(forecast);

            // 🎯 Simplified Response: AI takes 'message' field and shows it directly to user
            Response response = Response.success(formattedForecast);

            // 💾 Cache for potential repeat calls (Strike 2 protection)
            ToolCallGuard.cacheResult("getFinancialForecast", response);
            log.debug("Cached forecast result for strike 2 protection");

            return response;

        } catch (Exception e) {
            log.error("Error in getFinancialForecast: {}", e.getMessage(), e);
            return Response.error("Lỗi dự đoán: " + e.getMessage());
        }
    }

    /**
     * Format forecast into natural language based on user's question context.
     */
    private String formatForecast(ForecastResult forecast) {
        // Get user's original question for context-aware formatting
        String userQuestion = RequestContext.getUserQuestion();
        if (userQuestion == null) {
            userQuestion = "";
        }
        String questionLower = userQuestion.toLowerCase();

        // Detect question type with priority for specific patterns
        boolean askingTrend = questionLower.contains("xu hướng") ||
                             (questionLower.matches(".*(tăng|giảm).*") && questionLower.contains("hay"));
        boolean askingAmount = questionLower.contains("bao nhiêu") ||
                              questionLower.contains("how much");
        boolean askingBalance = questionLower.matches(".*(số dư|balance|cân đối).*");
        boolean askingWarning = questionLower.matches(".*(cảnh báo|warning|alert).*") &&
                               questionLower.contains("dòng tiền");

        // Extract category if mentioned (Vietnamese and English)
        String targetCategory = extractCategory(questionLower);

        StringBuilder formatted = new StringBuilder();
        ForecastResult.Prediction overall = forecast.getOverallPrediction();

        // If asking about specific category
        if (targetCategory != null && forecast.getCategoryPredictions() != null) {
            return formatCategorySpecific(forecast, targetCategory);
        }

        // If asking about trend only
        if (askingTrend && !askingAmount && !askingBalance && !askingWarning) {
            formatted.append("📈 Xu hướng chi tiêu:\n");
            formatted.append(String.format("- Xu hướng: %s (%.1f%%)\n",
                translateTrend(overall.getTrend()), Math.abs(overall.getChangePercent())));
            formatted.append("- Chi tiêu tháng này: ").append(formatVND(overall.getCurrentAmount())).append("\n");
            formatted.append("- Chi tiêu dự kiến tháng sau: ").append(formatVND(overall.getPredictedAmount())).append("\n");
            formatted.append(String.format("- Độ tin cậy: %.0f%%\n\n", overall.getConfidence() * 100));

            if (forecast.getExplanation() != null) {
                formatted.append("🤖 ").append(forecast.getExplanation());
            }
            return formatted.toString().trim();
        }

        // If asking about warnings only
        if (askingWarning && !askingAmount && !askingTrend) {
            if (forecast.getCashFlowWarning() != null) {
                ForecastResult.CashFlowWarning warning = forecast.getCashFlowWarning();
                formatted.append("⚠️ Cảnh báo dòng tiền:\n");
                formatted.append(String.format("- %s\n", warning.getMessage()));
                formatted.append(String.format("- Mức độ: %s\n", warning.getSeverity()));
                formatted.append(String.format("\n💡 Khuyến nghị: %s", warning.getRecommendation()));
            } else {
                formatted.append("✅ Không có cảnh báo dòng tiền.\n");
                formatted.append("Số dư dự kiến tháng sau: ").append(formatVND(forecast.getProjectedBalance()));
            }
            return formatted.toString().trim();
        }

        // Default: Return complete forecast
        formatted.append("📊 Dự đoán tháng tới:\n");
        formatted.append("- Chi tiêu dự kiến: ").append(formatVND(overall.getPredictedAmount())).append("\n");
        formatted.append("- Chi tiêu tháng này: ").append(formatVND(overall.getCurrentAmount())).append("\n");
        formatted.append(String.format("- Xu hướng: %s (%.1f%%)\n",
            translateTrend(overall.getTrend()), overall.getChangePercent()));
        formatted.append(String.format("- Độ tin cậy: %.0f%%\n\n", overall.getConfidence() * 100));

        // Top category predictions
        if (forecast.getCategoryPredictions() != null && !forecast.getCategoryPredictions().isEmpty()) {
            formatted.append("📈 Các danh mục chi tiêu dự kiến:\n");
            forecast.getCategoryPredictions().stream()
                .limit(5)
                .forEach(cat -> formatted.append(String.format("- %s: %s (%s %.1f%%)\n",
                    cat.getCategory(),
                    formatVND(cat.getPredictedAmount()),
                    cat.getTrend().equals("INCREASING") ? "↑" : cat.getTrend().equals("DECREASING") ? "↓" : "→",
                    Math.abs(cat.getChangePercent())
                )));
            formatted.append("\n");
        }

        // Projected balance
        if (forecast.getProjectedBalance() != null) {
            formatted.append("💰 Số dư dự kiến: ").append(formatVND(forecast.getProjectedBalance())).append("\n\n");
        }

        // Cash flow warning
        if (forecast.getCashFlowWarning() != null) {
            ForecastResult.CashFlowWarning warning = forecast.getCashFlowWarning();
            formatted.append(String.format("⚠️ %s\n", warning.getMessage()));
            formatted.append(String.format("💡 %s\n\n", warning.getRecommendation()));
        }

        // AI explanation
        if (forecast.getExplanation() != null) {
            formatted.append("🤖 Giải thích:\n");
            formatted.append(forecast.getExplanation()).append("\n");
        }

        return formatted.toString().trim();
    }

    /**
     * Format category-specific forecast.
     */
    private String formatCategorySpecific(ForecastResult forecast, String targetCategory) {
        // Find the target category in predictions
        ForecastResult.Prediction categoryPred = forecast.getCategoryPredictions().stream()
            .filter(cat -> cat.getCategory().toLowerCase().contains(targetCategory)
                        || targetCategory.contains(cat.getCategory().toLowerCase()))
            .findFirst()
            .orElse(null);

        StringBuilder formatted = new StringBuilder();

        if (categoryPred != null) {
            formatted.append(String.format("📊 Dự đoán chi tiêu %s tháng tới:\n", categoryPred.getCategory()));
            formatted.append("- Chi tiêu dự kiến: ").append(formatVND(categoryPred.getPredictedAmount())).append("\n");
            formatted.append("- Chi tiêu tháng này: ").append(formatVND(categoryPred.getCurrentAmount())).append("\n");
            formatted.append(String.format("- Xu hướng: %s (%.1f%%)\n",
                translateTrend(categoryPred.getTrend()), Math.abs(categoryPred.getChangePercent())));
            formatted.append(String.format("- Độ tin cậy: %.0f%%", categoryPred.getConfidence() * 100));
        } else {
            formatted.append(String.format("📊 Danh mục '%s':\n", targetCategory));
            formatted.append("Không có đủ dữ liệu lịch sử để dự đoán cho danh mục này.\n\n");
            formatted.append("💡 Các danh mục có dự đoán:\n");
            forecast.getCategoryPredictions().stream()
                .limit(5)
                .forEach(cat -> formatted.append(String.format("- %s\n", cat.getCategory())));
        }

        return formatted.toString().trim();
    }

    /**
     * Extract category name from question.
     */
    private String extractCategory(String questionLower) {
        // Common Vietnamese categories
        String[] categories = {
            "ăn uống", "mua sắm", "giao thông", "giải trí", "y tế", "giáo dục",
            "đầu tư", "tiết kiệm", "shopping", "food", "transport", "entertainment",
            "medical", "education", "investment"
        };

        for (String category : categories) {
            if (questionLower.contains(category)) {
                return category;
            }
        }

        return null;
    }

    private String formatVND(double amount) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("vi", "VN"));
        nf.setMaximumFractionDigits(0);
        return nf.format((long) amount) + " ₫";
    }

    private String translateTrend(String trend) {
        return switch (trend) {
            case "INCREASING" -> "Tăng";
            case "DECREASING" -> "Giảm";
            case "STABLE" -> "Ổn định";
            default -> trend;
        };
    }

    public record Request(
        @JsonProperty(required = true)
        String userId,

        @JsonProperty
        String forecastPeriod  // NEXT_MONTH, NEXT_QUARTER, etc.
    ) {}

    /**
     * Simplified Response - AI will take 'message' field and show it directly to user.
     */
    public record Response(
        boolean success,
        String message,  // Complete formatted message ready to show user
        String errorMessage
    ) {
        public static Response success(String message) {
            return new Response(true, message, null);
        }

        public static Response error(String error) {
            return new Response(false, null, error);
        }
    }
}
