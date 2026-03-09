package com.finance.aiservice.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.finance.aiservice.client.FinanceServiceClient;
import com.finance.aiservice.context.RequestContext;
import com.finance.aiservice.dto.TransactionDto;
import com.finance.aiservice.util.DataSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI Function Tool: Analyze Category Spending
 *
 * Enables AI to deep-dive into specific categories for questions like:
 * - "Why is my dining expense so high?"
 * - "How many times did I shop online this month?"
 * - "What's the average amount I spend on groceries?"
 *
 * Returns aggregated statistics + sample transactions.
 */
@Slf4j
@Component("analyzeCategory")
@Description("""
    Analyze spending for a SPECIFIC CATEGORY (not all categories).

    ✅✅✅ USE THIS TOOL when user mentions a category name:

    TRIGGER PATTERNS:
    - "tổng hợp chi tiêu [CATEGORY]" → Use this tool!
    - "tổng chi [CATEGORY]" → Use this tool!
    - "phân tích [CATEGORY]" → Use this tool!
    - "chi tiêu [CATEGORY] bao nhiêu" → Use this tool!
    - "analyze [CATEGORY]" → Use this tool!
    - "[CATEGORY] expenses" → Use this tool!

    EXAMPLES WHEN TO USE:
    ✅ "tổng hợp chi tiêu Mua sắm" → "Mua sắm" = category → Use this tool!
    ✅ "tổng hợp chi tiêu shopping" → "shopping" = category → Use this tool!
    ✅ "chi Ăn uống bao nhiêu" → "Ăn uống" = category → Use this tool!
    ✅ "tổng chi food" → "food" = category → Use this tool!
    ✅ "phân tích Giao thông" → "Giao thông" = category → Use this tool!
    ✅ "analyze shopping expenses" → "shopping" = category → Use this tool!

    🚨 BEFORE using this tool, you MUST:
    1. Call getUserCategories(userId) to get exact category names from database
    2. Match user's query to EXACT category name from the list
    3. Pass that EXACT category name to this tool

    ❌ NEVER guess category names (e.g., "Shopping", "Food")
    ❌ NEVER translate category names (e.g., "Mua sắm" → "Shopping")
    ✅ ALWAYS use exact name from getUserCategories

    Example workflow:
    User asks: "tổng hợp chi tiêu shopping"
    Step 1: Call getUserCategories(userId) → Returns ["Ăn uống", "Mua sắm", "Giao thông"]
    Step 2: Match "shopping" → "Mua sắm" (Vietnamese equivalent from list)
    Step 3: Call analyzeCategory(userId, category="Mua sắm", periodType="THIS_MONTH")
    """)
@RequiredArgsConstructor
public class AnalyzeCategoryTool implements Function<AnalyzeCategoryTool.Request, AnalyzeCategoryTool.Response> {

    private final FinanceServiceClient financeServiceClient;
    private final DataSanitizer dataSanitizer;

    @Override
    public Response apply(Request request) {
        log.info("AI Tool called: analyzeCategory for user {} (category: {}, period: {})",
            request.userId(), request.category(), request.periodType());

        try {
            // Calculate date range
            DateRange dateRange = calculateDateRange(request.periodType());

            // Check if we're in group mode (bankAccountIds available in RequestContext)
            List<String> groupBankAccountIds = RequestContext.getBankAccountIds();
            boolean isGroupMode = RequestContext.hasGroupBankAccounts();

            if (isGroupMode) {
                log.info("Group mode: analyzing category with bankAccountIds={}", groupBankAccountIds.size());
            }

            // Call FinanceService using existing filter endpoint with category parameter
            // Pass bankAccountIds for group mode (null for personal mode)
            ResponseEntity<com.finance.aiservice.dto.TransactionPage> response = financeServiceClient.getTransactionHistory(
                request.userId(),
                dateRange.startDate(),
                dateRange.endDate(),
                null,  // textSearch
                request.category(),  // category filter
                null,  // transactionType
                "transactionDate",  // sortBy
                "DESC",  // sortDirection
                0,  // page
                1000,  // size - get all transactions in category
                isGroupMode ? groupBankAccountIds : null  // bankAccountIds for group mode
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Response.error("Failed to fetch category transactions from FinanceService");
            }

            List<TransactionDto> transactions = response.getBody().content();

            if (transactions.isEmpty()) {
                return Response.success(
                    request.category(),
                    0L, 0L, 0L, 0L,
                    0, dateRange.startDate(), dateRange.endDate()
                );
            }

            // Calculate statistics
            // Extract amount from either amountOut (expense) or amountIn (income)
            LongSummaryStatistics stats = transactions.stream()
                .mapToLong(t -> {
                    if (t.amountOut() != null && t.amountOut() > 0) {
                        return t.amountOut().longValue();
                    } else if (t.amountIn() != null && t.amountIn() > 0) {
                        return t.amountIn().longValue();
                    }
                    return 0L;
                })
                .summaryStatistics();

            long totalAmount = stats.getSum();
            long avgAmount = (long) stats.getAverage();
            long maxAmount = stats.getMax();
            long minAmount = stats.getMin();
            int transactionCount = transactions.size();

            return Response.success(
                request.category(),
                totalAmount,
                avgAmount,
                maxAmount,
                minAmount,
                transactionCount,
                dateRange.startDate(),
                dateRange.endDate()
            );

        } catch (Exception e) {
            log.error("Error in analyzeCategory tool: {}", e.getMessage(), e);
            return Response.error("Error analyzing category: " + e.getMessage());
        }
    }

    /**
     * Calculate date range based on period type.
     */
    private DateRange calculateDateRange(String periodType) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;

        switch (periodType.toUpperCase()) {
            case "WEEK":
                startDate = endDate.minusWeeks(1);
                break;
            case "MONTH":
                startDate = endDate.minusMonths(1);
                break;
            case "THIS_MONTH":
                startDate = endDate.withDayOfMonth(1);
                break;
            case "LAST_MONTH":
                startDate = endDate.minusMonths(1).withDayOfMonth(1);
                endDate = endDate.minusMonths(1).withDayOfMonth(endDate.minusMonths(1).lengthOfMonth());
                break;
            case "YEAR":
                startDate = endDate.minusYears(1);
                break;
            default:
                startDate = endDate.minusMonths(1);  // Default: last month
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        return new DateRange(startDate.format(formatter), endDate.format(formatter));
    }

    private record DateRange(String startDate, String endDate) {}

    /**
     * Request DTO for AI function calling.
     */
    public record Request(
        @JsonProperty(required = true)
        @JsonPropertyDescription("User ID to analyze category for")
        String userId,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Category to analyze (e.g., DINING, SHOPPING, GROCERIES)")
        String category,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Time period: WEEK, MONTH, THIS_MONTH, LAST_MONTH, YEAR")
        String periodType
    ) {}

    /**
     * Response DTO returned to AI with category statistics.
     */
    public record Response(
        @JsonProperty("category")
        String category,

        @JsonProperty("totalAmount")
        Long totalAmount,

        @JsonProperty("averageAmount")
        Long averageAmount,

        @JsonProperty("maxAmount")
        Long maxAmount,

        @JsonProperty("minAmount")
        Long minAmount,

        @JsonProperty("transactionCount")
        Integer transactionCount,

        @JsonProperty("startDate")
        String startDate,

        @JsonProperty("endDate")
        String endDate,

        @JsonProperty("isError")
        Boolean isError,

        @JsonProperty("errorMessage")
        String errorMessage
    ) {
        public static Response success(String category, Long totalAmount, Long avgAmount,
                                       Long maxAmount, Long minAmount, Integer count,
                                       String startDate, String endDate) {
            return new Response(category, totalAmount, avgAmount, maxAmount, minAmount,
                count, startDate, endDate, false, null);
        }

        public static Response error(String errorMessage) {
            return new Response(null, null, null, null, null, null, null, null, true, errorMessage);
        }
    }
}
