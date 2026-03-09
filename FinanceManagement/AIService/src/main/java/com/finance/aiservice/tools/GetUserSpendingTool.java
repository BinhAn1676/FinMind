package com.finance.aiservice.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.finance.aiservice.client.FinanceServiceClient;
import com.finance.aiservice.context.RequestContext;
import com.finance.aiservice.dto.TransactionSummaryDto;
import com.finance.aiservice.util.DataSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * AI Function Tool: Get User Spending Summary
 *
 * Enables AI to query spending data for natural language questions like:
 * - "How much did I spend this week?"
 * - "What's my biggest expense category?"
 * - "Show me my spending for last month"
 *
 * SECURITY: Data is fetched from FinanceService, decrypted, and sanitized before returning to AI.
 */
@Slf4j
@Component("getUserSpending")
@Description("""
    ⛔⛔⛔ STOP! CHECK FOR CATEGORY FIRST! ⛔⛔⛔

    Before using this tool, scan user's query for ANY category name:
    - Vietnamese: Mua sắm, Ăn uống, Giao thông, Giải trí, Du lịch, Y tế, etc.
    - English: Shopping, Food, Transport, Entertainment, Travel, Healthcare, etc.

    IF YOU FIND A CATEGORY NAME IN THE QUERY:
    ⛔ DO NOT USE THIS TOOL!
    ✅ Use analyzeCategory instead

    ❌❌❌ WRONG EXAMPLES (Category mentioned - DO NOT use getUserSpending):
    ❌ "tổng hợp chi tiêu Mua sắm" → "Mua sắm" is category → Use analyzeCategory!
    ❌ "tổng hợp chi tiêu shopping" → "shopping" is category → Use analyzeCategory!
    ❌ "chi Ăn uống bao nhiêu" → "Ăn uống" is category → Use analyzeCategory!
    ❌ "tổng chi shopping" → "shopping" is category → Use analyzeCategory!
    ❌ "chi tiêu food bao nhiêu" → "food" is category → Use analyzeCategory!
    ❌ "analyze shopping expenses" → "shopping" is category → Use analyzeCategory!

    ✅✅✅ CORRECT EXAMPLES (NO category - OK to use getUserSpending):
    ✅ "Tháng này chi bao nhiêu?" ← No category mentioned
    ✅ "Tổng chi tháng này?" ← No category mentioned
    ✅ "Thu chi tháng vừa rồi?" ← No category mentioned
    ✅ "How much did I spend this month?" ← No category mentioned

    This tool gets TOTAL spending for ALL categories combined (not for a specific category).
    - "tổng thu" (total income) → use getUserSpending
    - "thu chi" (income and expense) → use getUserSpending

    DO NOT USE when user asks about individual transactions like:
    - "Khoản chi lớn nhất?" (biggest expense) → Use getTransactionHistory instead
    - "Chi nhỏ nhất?" (smallest expense) → Use getTransactionHistory instead
    - "Giao dịch gần đây?" (recent transactions) → Use getTransactionHistory instead
    - "Show my recent transactions" → Use getTransactionHistory instead

    REQUIRED PARAMETERS:
    1. userId (String) - User ID from context
    2. periodType (String) - MUST be one of these exact values:
       - "THIS_MONTH" for current month (tháng này, tháng hiện tại)
       - "LAST_MONTH" for previous month (tháng trước, tháng vừa rồi)
       - "TODAY" for today only (hôm nay)
       - "WEEK" for last 7 days (tuần này, 7 ngày qua)
       - "MONTH" for last 30 days (30 ngày qua)
       - "YEAR" for this year (năm nay)

    EXAMPLES:
    User: "tháng này chi bao nhiêu"
    Call: getUserSpending(userId="1", periodType="THIS_MONTH")

    User: "tháng vừa rồi thu chi"
    Call: getUserSpending(userId="1", periodType="LAST_MONTH")
    """)
@RequiredArgsConstructor
public class GetUserSpendingTool implements Function<GetUserSpendingTool.Request, GetUserSpendingTool.Response> {

    private final FinanceServiceClient financeServiceClient;
    private final DataSanitizer dataSanitizer;

    @Override
    public Response apply(Request request) {
        log.info("AI Tool called: getUserSpending for user {} (period: {})",
            request.userId(), request.periodType());

        try {
            // Calculate date range based on period
            DateRange dateRange = calculateDateRange(request.periodType());

            // Check if we're in group mode (bankAccountIds available in RequestContext)
            List<String> groupBankAccountIds = RequestContext.getBankAccountIds();
            boolean isGroupMode = RequestContext.hasGroupBankAccounts();

            if (isGroupMode) {
                log.info("Group mode: querying spending with bankAccountIds={}", groupBankAccountIds.size());
            }

            // Call FinanceService - pass bankAccountIds for group mode (null for personal mode)
            ResponseEntity<TransactionSummaryDto> response = financeServiceClient.getSpendingSummary(
                request.userId(),
                dateRange.startDate(),
                dateRange.endDate(),
                isGroupMode ? groupBankAccountIds : null
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Response.error("Failed to fetch spending data from FinanceService");
            }

            TransactionSummaryDto summary = response.getBody();

            // Sanitize category data (decrypt if needed, mask sensitive info)
            // Pass userId to ensure correct decryption key is used
            Map<String, Long> sanitizedExpenses = sanitizeCategoryData(request.userId(), summary.expenseByCategory());

            // Convert Double to Long for response (FinanceService returns Double)
            Long totalExpenseLong = summary.totalExpense() != null ? summary.totalExpense().longValue() : null;
            Long totalIncomeLong = summary.totalIncome() != null ? summary.totalIncome().longValue() : null;
            Long netAmountLong = summary.getNetAmount() != null ? summary.getNetAmount().longValue() : null;
            
            // Convert long to Integer for transactionCount
            Integer transactionCountInt = summary.transactionCount() != null ? summary.transactionCount().intValue() : null;

            return Response.success(
                totalExpenseLong,
                totalIncomeLong,
                netAmountLong,
                sanitizedExpenses,
                transactionCountInt,
                dateRange.startDate(),
                dateRange.endDate(),
                formatVND(totalExpenseLong),
                formatVND(totalIncomeLong),
                formatVND(netAmountLong)
            );

        } catch (Exception e) {
            log.error("Error in getUserSpending tool: {}", e.getMessage(), e);
            return Response.error("Error retrieving spending data: " + e.getMessage());
        }
    }

    /**
     * Calculate date range based on period type.
     */
    private DateRange calculateDateRange(String periodType) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate;

        switch (periodType.toUpperCase()) {
            case "TODAY":
                startDate = endDate;
                break;
            case "WEEK":
                startDate = endDate.minusWeeks(1);
                break;
            case "MONTH":
                startDate = endDate.minusMonths(1);
                break;
            case "YEAR":
                startDate = endDate.minusYears(1);
                break;
            case "THIS_MONTH":
                startDate = endDate.withDayOfMonth(1);
                break;
            case "LAST_MONTH":
                startDate = endDate.minusMonths(1).withDayOfMonth(1);
                endDate = endDate.minusMonths(1).withDayOfMonth(endDate.minusMonths(1).lengthOfMonth());
                break;
            default:
                startDate = endDate.minusMonths(1);  // Default: last month
        }

        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        return new DateRange(startDate.format(formatter), endDate.format(formatter));
    }

    /**
     * Format VND currency with Vietnamese thousand separators.
     * Example: 20445584 → "20.445.584 ₫"
     *
     * Manual formatting to ensure correct Vietnamese format (dots as thousand separators).
     */
    private String formatVND(Long amount) {
        if (amount == null) {
            return "0 ₫";
        }

        // Convert to string and manually add thousand separators
        String numStr = String.valueOf(Math.abs(amount));
        StringBuilder formatted = new StringBuilder();
        int len = numStr.length();

        for (int i = 0; i < len; i++) {
            if (i > 0 && (len - i) % 3 == 0) {
                formatted.append('.');
            }
            formatted.append(numStr.charAt(i));
        }

        // Add negative sign if needed
        if (amount < 0) {
            formatted.insert(0, '-');
        }

        return formatted.toString() + " ₫";
    }

    /**
     * Sanitize category data (mask merchant names if present, decrypt if needed).
     *
     * @param userId User ID - CRITICAL for getting the correct decryption key from KeyManagementService
     * @param categoryData Category data to sanitize
     */
    private Map<String, Long> sanitizeCategoryData(String userId, Map<String, Long> categoryData) {
        if (categoryData == null) {
            return new HashMap<>();
        }

        Map<String, Long> sanitized = new HashMap<>();
        for (Map.Entry<String, Long> entry : categoryData.entrySet()) {
            String category = entry.getKey();

            // If category contains encrypted data, decrypt and mask using USER's key
            // CRITICAL: Pass userId to get the correct user's AES key from KeyManagementService
            if (category.contains("ENCRYPTED:")) {
                category = dataSanitizer.decryptAndMask(userId, category);
            }

            sanitized.put(category, entry.getValue());
        }

        return sanitized;
    }

    private record DateRange(String startDate, String endDate) {}

    /**
     * Request DTO for AI function calling.
     * AI will populate these fields based on user's natural language query.
     */
    public record Request(
        @JsonProperty(required = true)
        @JsonPropertyDescription("User ID to get spending data for")
        String userId,

        @JsonProperty(required = true)
        @JsonPropertyDescription("""
            Time period - MUST be EXACTLY one of these values:
            - "THIS_MONTH": Current month from day 1 to today (use for: tháng này, tháng hiện tại)
            - "LAST_MONTH": Previous month complete (use for: tháng trước, tháng vừa rồi)
            - "TODAY": Today only (use for: hôm nay)
            - "WEEK": Last 7 days (use for: tuần này, 7 ngày qua)
            - "MONTH": Last 30 days (use for: 30 ngày qua)
            - "YEAR": This year from Jan 1 (use for: năm nay)
            IMPORTANT: Always provide this parameter - never leave it empty!
            """)
        String periodType
    ) {}

    /**
     * Response DTO returned to AI.
     * AI will use this data to formulate natural language response.
     */
    public record Response(
        @JsonProperty("totalExpense")
        @JsonPropertyDescription("Total expense amount in VND (raw number)")
        Long totalExpense,

        @JsonProperty("totalIncome")
        @JsonPropertyDescription("Total income amount in VND (raw number)")
        Long totalIncome,

        @JsonProperty("netBalance")
        @JsonPropertyDescription("Net balance in VND (raw number)")
        Long netBalance,

        @JsonProperty("totalExpenseFormatted")
        @JsonPropertyDescription("Total expense formatted with VND currency symbol - USE THIS for display")
        String totalExpenseFormatted,

        @JsonProperty("totalIncomeFormatted")
        @JsonPropertyDescription("Total income formatted with VND currency symbol - USE THIS for display")
        String totalIncomeFormatted,

        @JsonProperty("netBalanceFormatted")
        @JsonPropertyDescription("Net balance formatted with VND currency symbol - USE THIS for display")
        String netBalanceFormatted,

        @JsonProperty("expenseByCategory")
        Map<String, Long> expenseByCategory,

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
        public static Response success(Long totalExpense, Long totalIncome, Long netBalance,
                                       Map<String, Long> expenseByCategory, Integer transactionCount,
                                       String startDate, String endDate,
                                       String totalExpenseFormatted, String totalIncomeFormatted,
                                       String netBalanceFormatted) {
            return new Response(totalExpense, totalIncome, netBalance,
                totalExpenseFormatted, totalIncomeFormatted, netBalanceFormatted,
                expenseByCategory, transactionCount, startDate, endDate, false, null);
        }

        public static Response error(String errorMessage) {
            return new Response(null, null, null, null, null, null, null, null, null, null, true, errorMessage);
        }
    }
}
