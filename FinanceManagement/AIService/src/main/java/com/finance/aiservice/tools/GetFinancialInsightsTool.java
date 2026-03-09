package com.finance.aiservice.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.finance.aiservice.context.RequestContext;
import com.finance.aiservice.context.ToolCallGuard;
import com.finance.aiservice.engine.AnalysisRequest;
import com.finance.aiservice.engine.AnalysisResult;
import com.finance.aiservice.service.HybridEngineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI Tool: Get Financial Insights
 *
 * Provides intelligent financial insights using Hybrid Engine.
 * Protected with RequestContext (userId override) and ToolCallGuard (anti-loop).
 */
@Slf4j
@Component("getFinancialInsights")
@Description("""
    Get intelligent financial insights, alerts, and recommendations.

    🚨 CALL THIS when user asks about:
    - "tình hình tài chính?" / "financial situation?"
    - "có cảnh báo gì không?" / "any alerts?"
    - "khuyến nghị gì?" / "recommendations?"
    - "sức khỏe tài chính?" / "financial health?"
    - "phân tích toàn diện" / "comprehensive analysis"

    Returns REAL DATA from analysis engine.

    🛑 Call ONCE then respond in VIETNAMESE.
    """)
@RequiredArgsConstructor
public class GetFinancialInsightsTool implements Function<GetFinancialInsightsTool.Request, GetFinancialInsightsTool.Response> {

    private final HybridEngineService hybridEngineService;

    @Override
    public Response apply(Request request) {
        log.info("AI Tool called: getFinancialInsights for user {}", request.userId());

        // 🛑 GUARD: Prevent infinite loops (3-strike approach)
        int callCount = ToolCallGuard.incrementAndGetCallCount("getFinancialInsights");
        log.info("📊 getFinancialInsights call count: {}", callCount);

        if (callCount == 2) {
            // Strike 2: Return cached result
            log.warn("🚨 Strike 2: Returning cached result");
            Response cached = ToolCallGuard.getCachedResult("getFinancialInsights", Response.class);
            if (cached != null) {
                return cached;
            }
        } else if (callCount >= 3) {
            // Strike 3: Force stop with error
            log.error("🛑 Strike 3: Forcing STOP");
            return Response.error("DỪNG! Đã cung cấp insights rồi. Hãy dùng kết quả đã có.");
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
            String periodType = request.periodType() != null ? request.periodType() : "THIS_MONTH";
            List<AnalysisResult.Insight> allInsights = new ArrayList<>();
            List<AnalysisResult.Recommendation> allRecommendations = new ArrayList<>();
            Integer healthScore = null;

            // 1. Spending alerts
            AnalysisResult spendingResult = hybridEngineService.analyze(
                AnalysisRequest.builder()
                    .analysisType(AnalysisRequest.AnalysisType.SPENDING_ALERT)
                    .userId(userId)
                    .periodType(periodType)
                    .build()
            );
            if (spendingResult.isSuccess() && spendingResult.getInsights() != null) {
                allInsights.addAll(spendingResult.getInsights());
            }

            // 2. Savings recommendations
            AnalysisResult savingsResult = hybridEngineService.analyze(
                AnalysisRequest.builder()
                    .analysisType(AnalysisRequest.AnalysisType.SAVINGS_RECOMMENDATION)
                    .userId(userId)
                    .periodType(periodType)
                    .build()
            );
            if (savingsResult.isSuccess() && savingsResult.getRecommendations() != null) {
                allRecommendations.addAll(savingsResult.getRecommendations());
            }

            // 3. Financial health score
            AnalysisResult healthResult = hybridEngineService.analyze(
                AnalysisRequest.builder()
                    .analysisType(AnalysisRequest.AnalysisType.FINANCIAL_HEALTH_SCORE)
                    .userId(userId)
                    .periodType(periodType)  // ← FIX: Add periodType for date filtering
                    .build()
            );
            if (healthResult.isSuccess()) {
                healthScore = healthResult.getHealthScore();
                if (healthResult.getInsights() != null) {
                    allInsights.addAll(healthResult.getInsights());
                }
            }

            // Build response
            Response response = Response.success(
                String.format("Phân tích: %d insights, %d recommendations",
                    allInsights.size(), allRecommendations.size()),
                formatInsights(allInsights, allRecommendations, healthScore),
                allInsights.size() + allRecommendations.size(),
                healthScore
            );

            // 💾 Cache for potential repeat calls
            ToolCallGuard.cacheResult("getFinancialInsights", response);
            log.debug("✅ Cached result for Strike 2");

            return response;

        } catch (Exception e) {
            log.error("Error in getFinancialInsights: {}", e.getMessage(), e);
            return Response.error("Lỗi phân tích insights: " + e.getMessage());
        }
    }

    /**
     * Format insights into natural language.
     */
    private String formatInsights(List<AnalysisResult.Insight> insights,
                                   List<AnalysisResult.Recommendation> recommendations,
                                   Integer healthScore) {
        StringBuilder formatted = new StringBuilder();

        if (healthScore != null) {
            formatted.append(String.format("📊 Điểm sức khỏe tài chính: %d/100\n\n", healthScore));
        }

        if (!insights.isEmpty()) {
            formatted.append("💡 Cảnh báo:\n");
            for (AnalysisResult.Insight insight : insights) {
                formatted.append(String.format("- %s\n", insight.getMessage()));
            }
            formatted.append("\n");
        }

        if (!recommendations.isEmpty()) {
            formatted.append("✅ Khuyến nghị:\n");
            for (AnalysisResult.Recommendation rec : recommendations.stream().limit(3).collect(Collectors.toList())) {
                formatted.append(String.format("- %s: %s\n", rec.getTitle(), rec.getDescription()));
            }
        }

        return formatted.toString().trim();
    }

    public record Request(
        @JsonProperty(required = true)
        String userId,

        @JsonProperty
        String periodType  // THIS_MONTH, LAST_MONTH, etc.
    ) {}

    public record Response(
        boolean success,
        String summary,
        String formattedInsights,
        Integer insightCount,
        Integer healthScore,
        String errorMessage
    ) {
        public static Response success(String summary, String formatted, Integer count, Integer score) {
            return new Response(true, summary, formatted, count, score, null);
        }

        public static Response error(String error) {
            return new Response(false, null, null, 0, null, error);
        }
    }
}
