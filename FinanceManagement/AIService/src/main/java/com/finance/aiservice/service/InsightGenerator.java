package com.finance.aiservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.aiservice.client.FinanceServiceClient;
import com.finance.aiservice.dto.InsightCardDto;
import com.finance.aiservice.dto.TransactionSummaryDto;
import com.finance.aiservice.util.DataSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates AI-powered insight cards for the dashboard UI.
 *
 * This service:
 * 1. Takes sanitized transaction data
 * 2. Analyzes patterns using Ollama
 * 3. Returns strict JSON array of InsightCardDto
 *
 * UI Integration:
 * Frontend calls GET /api/ai/insights?userId={id}
 * → Returns List<InsightCardDto>
 * → Angular renders cards on dashboard
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InsightGenerator {

    @Qualifier("jsonChatClient")
    private final ChatClient jsonChatClient;

    private final DataSanitizer dataSanitizer;
    private final ObjectMapper objectMapper;
    private final FinanceServiceClient financeServiceClient;

    /**
     * Generate insight cards for a user by fetching their transaction data.
     *
     * @param userId User ID
     * @return List of insight cards for UI display
     */
    public List<InsightCardDto> generateInsightsForUser(String userId) {
        log.info("Generating insights for user {}", userId);

        try {
            // Fetch transaction summary from FinanceService
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusMonths(1);  // Last 30 days
            DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;

            ResponseEntity<TransactionSummaryDto> response = financeServiceClient.getSpendingSummary(
                userId,
                startDate.format(formatter),
                endDate.format(formatter),
                null
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Failed to fetch transaction summary for user {}", userId);
                return createFallbackInsights();
            }

            TransactionSummaryDto summary = response.getBody();

            // Convert to format for AI processing
            Map<String, Object> summaryMap = new HashMap<>();
            summaryMap.put("userId", summary.userId());
            summaryMap.put("totalIncome", summary.totalIncome());
            summaryMap.put("totalExpense", summary.totalExpense());
            summaryMap.put("netBalance", summary.getNetAmount());  // Use getNetAmount() which handles both netAmount and netBalance
            summaryMap.put("netAmount", summary.getNetAmount());  // Also include as netAmount for clarity
            summaryMap.put("expenseByCategory", summary.expenseByCategory());
            summaryMap.put("incomeByCategory", summary.incomeByCategory());
            summaryMap.put("transactionCount", summary.transactionCount());
            summaryMap.put("averageAmount", summary.averageAmount());

            // Generate insights from summary
            return generateInsightsFromSummary(summaryMap, userId);

        } catch (Exception e) {
            log.error("Failed to generate insights for user {}: {}", userId, e.getMessage(), e);
            return createFallbackInsights();
        }
    }

    /**
     * Generate insight cards from transaction summary data.
     *
     * @param summary Transaction summary data
     * @param userId User ID for context
     * @return List of insight cards for UI display
     */
    public List<InsightCardDto> generateInsightsFromSummary(
        Map<String, Object> summary,
        String userId
    ) {
        log.info("Generating insights from summary for user {}", userId);

        try {
            // Build prompt with summary data
            String prompt = buildInsightPromptFromSummary(summary);

            // Call AI to generate JSON
            String jsonResponse = jsonChatClient
                .prompt()
                .user(prompt)
                .call()
                .content();

            log.debug("AI JSON Response: {}", jsonResponse);

            // Parse JSON to DTOs
            List<InsightCardDto> insights = parseInsightCards(jsonResponse);

            // Validate insights
            List<InsightCardDto> validInsights = insights.stream()
                .filter(this::validateInsight)
                .toList();

            log.info("Generated {} valid insight cards for user {}", validInsights.size(), userId);
            return validInsights;

        } catch (Exception e) {
            log.error("Failed to generate insights for user {}: {}", userId, e.getMessage(), e);
            return createFallbackInsights();
        }
    }

    /**
     * Generate insight cards from transaction data (legacy method).
     *
     * @param sanitizedTransactions List of sanitized transactions (already masked)
     * @param userId User ID for context
     * @return List of insight cards for UI display
     */
    public List<InsightCardDto> generateInsights(
        List<Map<String, Object>> sanitizedTransactions,
        String userId
    ) {
        log.info("Generating insights for user {} with {} transactions",
            userId, sanitizedTransactions.size());

        try {
            // Build prompt with transaction summary
            String prompt = buildInsightPrompt(sanitizedTransactions);

            // Call AI to generate JSON
            String jsonResponse = jsonChatClient
                .prompt()
                .user(prompt)
                .call()
                .content();

            log.debug("AI JSON Response: {}", jsonResponse);

            // Parse JSON to DTOs
            List<InsightCardDto> insights = parseInsightCards(jsonResponse);

            log.info("Generated {} insight cards for user {}", insights.size(), userId);
            return insights;

        } catch (Exception e) {
            log.error("Failed to generate insights for user {}: {}", userId, e.getMessage(), e);
            // Return fallback insight card on error
            return createFallbackInsights();
        }
    }

    /**
     * Build AI prompt for insight generation from summary.
     */
    @SuppressWarnings("unchecked")
    private String buildInsightPromptFromSummary(Map<String, Object> summary) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze this financial summary and generate insight cards.\n\n");

        // Add summary data
        prompt.append("Financial Summary:\n");
        prompt.append(String.format("- Total Income: %s VND\n",
            formatAmount(summary.get("totalIncome"))));
        prompt.append(String.format("- Total Expense: %s VND\n",
            formatAmount(summary.get("totalExpense"))));
        prompt.append(String.format("- Net Balance: %s VND\n",
            formatAmount(summary.get("netBalance"))));
        prompt.append(String.format("- Transaction Count: %s\n",
            summary.getOrDefault("transactionCount", "N/A")));

        // Add category breakdown
        Object expenseByCategory = summary.get("expenseByCategory");
        if (expenseByCategory instanceof Map) {
            prompt.append("\nExpense by Category (VND):\n");
            ((Map<String, Object>) expenseByCategory).forEach((category, amount) ->
                prompt.append(String.format("- %s: %s VND\n", category, formatAmount(amount)))
            );
        }

        // Add instructions (same as before)
        appendInsightInstructions(prompt);

        return prompt.toString();
    }

    /**
     * Build AI prompt for insight generation.
     */
    private String buildInsightPrompt(List<Map<String, Object>> transactions) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Analyze these sanitized transactions and generate insight cards.\n\n");

        // Add transaction summary
        prompt.append("Transaction Summary:\n");
        prompt.append(String.format("- Total Transactions: %d\n", transactions.size()));

        // Calculate totals by category
        Map<String, Double> categoryTotals = transactions.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    tx -> (String) tx.getOrDefault("category", "UNKNOWN"),
                    java.util.stream.Collectors.summingDouble(
                        tx -> ((Number) tx.getOrDefault("amount", 0)).doubleValue()
                    )
                )
            );

        prompt.append("\nSpending by Category (VND):\n");
        categoryTotals.forEach((category, total) ->
            prompt.append(String.format("- %s: %.0f VND\n", category, total))
        );

        // Add instructions
        appendInsightInstructions(prompt);

        return prompt.toString();
    }

    /**
     * Append common insight generation instructions to prompt.
     */
    private void appendInsightInstructions(StringBuilder prompt) {
        prompt.append("\n--- INSTRUCTIONS ---\n");
        prompt.append("Generate 2-4 insight cards as a JSON array.\n");
        prompt.append("Each card should be one of:\n");
        prompt.append("1. WARNING: High spending alerts, budget overruns\n");
        prompt.append("2. SAVING: Optimization suggestions, potential savings\n");
        prompt.append("3. INFO: General insights, positive trends\n\n");

        prompt.append("Required JSON Schema:\n");
        prompt.append("[\n");
        prompt.append("  {\n");
        prompt.append("    \"type\": \"WARNING|SAVING|INFO\",\n");
        prompt.append("    \"title\": \"Short headline (max 50 chars)\",\n");
        prompt.append("    \"amount\": <number in VND>,\n");
        prompt.append("    \"message\": \"Detailed explanation (max 200 chars, in Vietnamese)\",\n");
        prompt.append("    \"action\": \"CTA text (max 20 chars)\",\n");
        prompt.append("    \"category\": \"Optional category name\",\n");
        prompt.append("    \"severity\": <1-5 for WARNING only>\n");
        prompt.append("  }\n");
        prompt.append("]\n\n");

        prompt.append("Rules:\n");
        prompt.append("- Respond ONLY with valid JSON array (no markdown, no explanations)\n");
        prompt.append("- Use Vietnamese for 'message' field\n");
        prompt.append("- Amounts in VND (Vietnamese Dong)\n");
        prompt.append("- Be specific and actionable\n");
        prompt.append("- Prioritize most impactful insights\n");
    }

    /**
     * Format amount for display.
     */
    private String formatAmount(Object amount) {
        if (amount == null) {
            return "0";
        }
        if (amount instanceof Number) {
            return String.format("%,d", ((Number) amount).longValue());
        }
        return amount.toString();
    }

    /**
     * Parse AI JSON response to InsightCardDto list.
     */
    private List<InsightCardDto> parseInsightCards(String jsonResponse) {
        try {
            // Clean response (remove markdown if present)
            String cleanJson = cleanJsonResponse(jsonResponse);

            // Parse JSON array
            return objectMapper.readValue(
                cleanJson,
                new TypeReference<List<InsightCardDto>>() {}
            );

        } catch (Exception e) {
            log.error("Failed to parse insight cards JSON: {}", e.getMessage());
            throw new RuntimeException("Invalid JSON from AI", e);
        }
    }

    /**
     * Clean JSON response by removing markdown code blocks.
     */
    private String cleanJsonResponse(String response) {
        if (response == null) {
            return "[]";
        }

        // Remove markdown code blocks
        String cleaned = response.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        }
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }

    /**
     * Create fallback insights when AI fails.
     */
    private List<InsightCardDto> createFallbackInsights() {
        List<InsightCardDto> fallback = new ArrayList<>();

        fallback.add(InsightCardDto.createInfo(
            "Phân Tích Đang Xử Lý",
            0L,
            "Hệ thống đang phân tích dữ liệu chi tiêu của bạn. Vui lòng thử lại sau.",
            null
        ));

        return fallback;
    }

    /**
     * Validate generated insights before returning to frontend.
     */
    private boolean validateInsight(InsightCardDto insight) {
        if (insight.type() == null) {
            log.warn("Invalid insight: missing type");
            return false;
        }

        if (insight.title() == null || insight.title().isBlank()) {
            log.warn("Invalid insight: missing title");
            return false;
        }

        if (insight.message() == null || insight.message().isBlank()) {
            log.warn("Invalid insight: missing message");
            return false;
        }

        // Additional validations
        if (insight.title().length() > 50) {
            log.warn("Insight title too long: {}", insight.title().length());
        }

        if (insight.message().length() > 200) {
            log.warn("Insight message too long: {}", insight.message().length());
        }

        return true;
    }
}
