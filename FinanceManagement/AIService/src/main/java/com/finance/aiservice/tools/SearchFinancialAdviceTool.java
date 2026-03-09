package com.finance.aiservice.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.finance.aiservice.entity.FinancialKnowledge;
import com.finance.aiservice.service.RagRetrievalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI Function Tool: Search Financial Advice
 *
 * Enables AI to search the financial knowledge base for relevant advice.
 * Uses semantic search (RAG) to find the most relevant tips based on user questions.
 *
 * Example questions:
 * - "How can I save more money?" → Returns advice on savings strategies
 * - "What's the best way to track expenses?" → Returns budgeting tips
 * - "How much should I keep for emergencies?" → Returns emergency fund advice
 */
@Slf4j
@Component("searchFinancialAdvice")
@Description("""
    Search the financial knowledge base for relevant advice and tips.

    USE THIS WHEN user asks questions about:
    - How to save money ("Làm sao để tiết kiệm?", "How to save?")
    - Budgeting strategies ("Cách lập ngân sách", "How to budget?")
    - Emergency funds ("Quỹ khẩn cấp là gì?", "What is emergency fund?")
    - Expense tracking ("Theo dõi chi tiêu", "How to track expenses?")
    - General financial advice ("Lời khuyên tài chính", "Financial advice")

    This tool uses semantic search to find the most relevant advice from the knowledge base.

    Parameters:
    - query: Required - User's question or topic in natural language
    - limit: Optional - Max number of advice entries to return (default: 3, max: 5)

    Examples:
    - "How to save money monthly?" → searchFinancialAdvice(query="monthly savings tips", limit=3)
    - "Emergency fund advice" → searchFinancialAdvice(query="emergency fund", limit=2)
    - "Làm sao để giảm chi tiêu?" → searchFinancialAdvice(query="reduce spending tips", limit=3)
    """)
@RequiredArgsConstructor
public class SearchFinancialAdviceTool implements Function<SearchFinancialAdviceTool.Request, SearchFinancialAdviceTool.Response> {

    private final RagRetrievalService ragRetrievalService;

    @Override
    public Response apply(Request request) {
        log.info("AI Tool called: searchFinancialAdvice (query: {}, limit: {})",
            request.query(), request.limit());

        try {
            // Validate query
            if (request.query() == null || request.query().trim().isEmpty()) {
                return Response.error("Query cannot be empty");
            }

            int limit = request.limit() != null ? request.limit() : 3;

            // Cap limit to prevent overwhelming the AI
            if (limit > 5) {
                limit = 5;
            }

            // Search for relevant financial advice using RAG
            List<FinancialKnowledge> results = ragRetrievalService.searchFinancialAdvice(
                request.query(),
                limit,
                0.7  // 70% minimum similarity
            );

            if (results.isEmpty()) {
                return Response.success(
                    List.of(),
                    "No relevant financial advice found for your question. Try rephrasing or ask about specific topics like savings, budgeting, or emergency funds."
                );
            }

            // Convert to simple DTO for AI
            List<AdviceEntry> adviceEntries = results.stream()
                .map(knowledge -> new AdviceEntry(
                    knowledge.getTopic(),
                    knowledge.getContent(),
                    knowledge.getCategory()
                ))
                .collect(Collectors.toList());

            return Response.success(
                adviceEntries,
                String.format("Found %d relevant financial advice entries", results.size())
            );

        } catch (Exception e) {
            log.error("Error in searchFinancialAdvice tool: {}", e.getMessage(), e);
            return Response.error("Failed to search financial advice: " + e.getMessage());
        }
    }

    /**
     * Request parameters for searching financial advice.
     */
    public record Request(
        @JsonProperty(required = true)
        @JsonPropertyDescription("User's question or topic in natural language (e.g., 'how to save money', 'emergency fund tips')")
        String query,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Maximum number of advice entries to return (default: 3, max: 5)")
        Integer limit
    ) {}

    /**
     * Response containing financial advice results.
     */
    public record Response(
        @JsonProperty("success")
        boolean success,

        @JsonProperty("advice")
        List<AdviceEntry> advice,

        @JsonProperty("message")
        String message,

        @JsonProperty("error")
        String error
    ) {
        public static Response success(List<AdviceEntry> advice, String message) {
            return new Response(true, advice, message, null);
        }

        public static Response error(String error) {
            return new Response(false, List.of(), null, error);
        }
    }

    /**
     * Single advice entry (simplified for AI consumption).
     */
    public record AdviceEntry(
        @JsonProperty("topic")
        String topic,

        @JsonProperty("content")
        String content,

        @JsonProperty("category")
        String category
    ) {}
}
