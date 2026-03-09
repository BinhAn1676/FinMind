package com.finance.aiservice.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.aiservice.client.FinanceServiceClient;
import com.finance.aiservice.dto.TransactionDto;
import com.finance.aiservice.dto.TransactionSummaryDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI Reasoning Engine - uses Ollama to enhance rule-based analysis.
 *
 * Part of the Hybrid Engine architecture:
 * 1. RuleBasedEngine runs first → produces structured results (fast, deterministic)
 * 2. AiReasoningEngine takes those results + transaction data → enhances with AI (deeper, contextual)
 * 3. HybridEngineService merges both → returns comprehensive HYBRID result
 *
 * AI Reasoning adds value by:
 * - Validating rule-based conclusions with contextual reasoning
 * - Discovering patterns and insights that static rules cannot capture
 * - Providing narrative assessments in natural Vietnamese language
 * - Generating culturally-relevant, personalized financial advice
 *
 * Graceful degradation: if AI fails, the system falls back to rule-based results only.
 */
@Slf4j
@Component
public class AiReasoningEngine {

    private final ChatClient hybridAnalysisChatClient;
    private final FinanceServiceClient financeServiceClient;
    private final ObjectMapper objectMapper;

    @Value("${app.ai.hybrid-engine.ai-timeout-seconds:30}")
    private int aiTimeoutSeconds;

    public AiReasoningEngine(
        @Qualifier("hybridAnalysisChatClient") ChatClient hybridAnalysisChatClient,
        FinanceServiceClient financeServiceClient,
        ObjectMapper objectMapper
    ) {
        this.hybridAnalysisChatClient = hybridAnalysisChatClient;
        this.financeServiceClient = financeServiceClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Enhance rule-based analysis with AI reasoning.
     *
     * @param ruleBasedResult The result from RuleBasedEngine
     * @param request         The original analysis request
     * @return Enhanced AnalysisResult with AI insights merged in, or null if AI fails
     */
    public AiEnhancement enhance(AnalysisResult ruleBasedResult, AnalysisRequest request) {
        log.info("AI Reasoning Engine enhancing: type={}, userId={}",
            request.getAnalysisType(), request.getUserId());

        try {
            // Fetch transaction summary for AI context
            String transactionContext = buildTransactionContext(request);

            // Build prompt with rule-based results + transaction data
            String prompt = buildEnhancementPrompt(ruleBasedResult, request, transactionContext);

            // Call Ollama for AI reasoning
            String jsonResponse = hybridAnalysisChatClient
                .prompt()
                .user(prompt)
                .call()
                .content();

            log.debug("AI Reasoning response: {}", jsonResponse);

            // Parse AI response
            AiEnhancement enhancement = parseAiResponse(jsonResponse);

            if (enhancement != null) {
                log.info("AI Reasoning completed: {} enhanced insights, confidence={}",
                    enhancement.enhancedInsights().size(), enhancement.confidence());
            }

            return enhancement;

        } catch (Exception e) {
            log.warn("AI Reasoning failed (will fall back to rule-based only): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build transaction context string for AI prompt.
     */
    private String buildTransactionContext(AnalysisRequest request) {
        try {
            // Determine date range
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusMonths(1);

            if (request.getStartDate() != null && request.getEndDate() != null) {
                startDate = LocalDate.parse(request.getStartDate());
                endDate = LocalDate.parse(request.getEndDate());
            } else if ("LAST_MONTH".equals(request.getPeriodType())) {
                startDate = endDate.minusMonths(1).withDayOfMonth(1);
                endDate = endDate.minusMonths(1).withDayOfMonth(endDate.minusMonths(1).lengthOfMonth());
            } else if ("THIS_MONTH".equals(request.getPeriodType())) {
                startDate = endDate.withDayOfMonth(1);
            }

            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

            // Fetch spending summary (personal context - no bankAccountIds)
            ResponseEntity<TransactionSummaryDto> summaryResponse = financeServiceClient.getSpendingSummary(
                request.getUserId(),
                startDate.format(formatter),
                endDate.format(formatter),
                null
            );

            if (!summaryResponse.getStatusCode().is2xxSuccessful() || summaryResponse.getBody() == null) {
                return "Không có dữ liệu giao dịch khả dụng.";
            }

            TransactionSummaryDto summary = summaryResponse.getBody();
            StringBuilder context = new StringBuilder();
            context.append(String.format("Tổng thu: %,.0f VND\n", safeDouble(summary.totalIncome())));
            context.append(String.format("Tổng chi: %,.0f VND\n", safeDouble(summary.totalExpense())));
            context.append(String.format("Số dư ròng: %,.0f VND\n", safeDouble(summary.getNetAmount())));
            context.append(String.format("Số giao dịch: %s\n", summary.transactionCount()));

            if (summary.expenseByCategory() != null && !summary.expenseByCategory().isEmpty()) {
                context.append("\nChi tiêu theo danh mục:\n");
                summary.expenseByCategory().forEach((category, amount) ->
                    context.append(String.format("- %s: %,.0f VND\n", category, safeDouble(amount)))
                );
            }

            return context.toString();

        } catch (Exception e) {
            log.warn("Failed to build transaction context: {}", e.getMessage());
            return "Không thể truy xuất dữ liệu giao dịch chi tiết.";
        }
    }

    /**
     * Build the enhancement prompt for AI.
     */
    private String buildEnhancementPrompt(AnalysisResult ruleResult, AnalysisRequest request, String transactionContext) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("=== PHÂN TÍCH CẦN NÂNG CAO ===\n");
        prompt.append(String.format("Loại phân tích: %s\n", request.getAnalysisType()));
        prompt.append(String.format("Kỳ phân tích: %s\n\n", request.getPeriodType() != null ? request.getPeriodType() : "Mặc định"));

        // Rule-based results
        prompt.append("=== KẾT QUẢ TỪ RULE-BASED ENGINE ===\n");

        if (ruleResult.getHealthScore() != null) {
            prompt.append(String.format("Điểm sức khỏe tài chính: %d/100\n", ruleResult.getHealthScore()));
        }

        prompt.append(String.format("Tóm tắt: %s\n", ruleResult.getInsight()));
        prompt.append(String.format("Độ tin cậy Rule-based: %.0f%%\n\n", ruleResult.getConfidence() * 100));

        if (ruleResult.getInsights() != null && !ruleResult.getInsights().isEmpty()) {
            prompt.append("Cảnh báo đã phát hiện:\n");
            for (AnalysisResult.Insight insight : ruleResult.getInsights()) {
                prompt.append(String.format("- [%s] %s", insight.getSeverity(), insight.getMessage()));
                if (insight.getAmount() != null) {
                    prompt.append(String.format(" (%.0f VND)", insight.getAmount()));
                }
                prompt.append("\n");
            }
            prompt.append("\n");
        }

        if (ruleResult.getRecommendations() != null && !ruleResult.getRecommendations().isEmpty()) {
            prompt.append("Khuyến nghị hiện tại:\n");
            for (AnalysisResult.Recommendation rec : ruleResult.getRecommendations()) {
                prompt.append(String.format("- %s: %s\n", rec.getTitle(), rec.getDescription()));
            }
            prompt.append("\n");
        }

        // Transaction data
        prompt.append("=== DỮ LIỆU GIAO DỊCH ===\n");
        prompt.append(transactionContext);
        prompt.append("\n");

        // Instructions
        prompt.append("=== NHIỆM VỤ CỦA BẠN ===\n");
        prompt.append("Hãy phân tích dữ liệu trên và:\n");
        prompt.append("1. VALIDATE: Xác nhận hoặc chỉ ra điểm chưa chính xác trong kết quả rule-based\n");
        prompt.append("2. ENHANCE: Thêm insights sâu hơn mà rules không phát hiện được\n");
        prompt.append("3. DISCOVER: Phát hiện patterns hoặc rủi ro tiềm ẩn\n");
        prompt.append("4. ADVISE: Đưa ra lời khuyên cụ thể, phù hợp với bối cảnh tài chính Việt Nam\n\n");
        prompt.append("Trả lời bằng JSON theo schema đã định nghĩa. Tất cả nội dung user-facing bằng tiếng Việt.\n");

        return prompt.toString();
    }

    /**
     * Parse AI JSON response into AiEnhancement record.
     */
    private AiEnhancement parseAiResponse(String jsonResponse) {
        try {
            String cleanJson = cleanJsonResponse(jsonResponse);
            Map<String, Object> parsed = objectMapper.readValue(cleanJson, new TypeReference<>() {});

            String validationNotes = (String) parsed.getOrDefault("validationNotes", "");
            String aiNarrative = (String) parsed.getOrDefault("aiNarrative", "");
            Double confidence = parseDouble(parsed.get("confidence"), 0.7);
            Integer adjustedHealthScore = parseInteger(parsed.get("adjustedHealthScore"));

            // Parse enhanced insights
            List<AnalysisResult.Insight> enhancedInsights = new ArrayList<>();
            Object insightsRaw = parsed.get("enhancedInsights");
            if (insightsRaw instanceof List<?> insightsList) {
                for (Object item : insightsList) {
                    if (item instanceof Map<?, ?> map) {
                        Object severityVal = map.get("severity");
                        String severity = severityVal != null ? severityVal.toString() : "MEDIUM";
                        enhancedInsights.add(AnalysisResult.Insight.builder()
                            .category((String) map.get("category"))
                            .message((String) map.get("message"))
                            .severity(severity)
                            .recommendation((String) map.get("recommendation"))
                            .build());
                    }
                }
            }

            // Parse additional recommendations
            List<AnalysisResult.Recommendation> additionalRecs = new ArrayList<>();
            Object recsRaw = parsed.get("additionalRecommendations");
            if (recsRaw instanceof List<?> recsList) {
                for (Object item : recsList) {
                    if (item instanceof Map<?, ?> map) {
                        additionalRecs.add(AnalysisResult.Recommendation.builder()
                            .title((String) map.get("title"))
                            .description((String) map.get("description"))
                            .impactScore(parseInteger(map.get("impactScore")))
                            .build());
                    }
                }
            }

            return new AiEnhancement(
                validationNotes,
                aiNarrative,
                enhancedInsights,
                additionalRecs,
                adjustedHealthScore,
                confidence
            );

        } catch (Exception e) {
            log.error("Failed to parse AI reasoning response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Clean JSON response by removing markdown code blocks.
     */
    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        return cleaned.trim();
    }

    private double safeDouble(Object value) {
        if (value == null) return 0.0;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); } catch (Exception e) { return 0.0; }
    }

    private Double parseDouble(Object value, double defaultVal) {
        if (value == null) return defaultVal;
        if (value instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(value.toString()); } catch (Exception e) { return defaultVal; }
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(value.toString()); } catch (Exception e) { return null; }
    }

    /**
     * Result of AI reasoning enhancement.
     */
    public record AiEnhancement(
        String validationNotes,
        String aiNarrative,
        List<AnalysisResult.Insight> enhancedInsights,
        List<AnalysisResult.Recommendation> additionalRecommendations,
        Integer adjustedHealthScore,
        Double confidence
    ) {}
}
