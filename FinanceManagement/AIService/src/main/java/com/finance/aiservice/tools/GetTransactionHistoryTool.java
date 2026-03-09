package com.finance.aiservice.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.finance.aiservice.client.FinanceServiceClient;
import com.finance.aiservice.context.RequestContext;
import com.finance.aiservice.dto.TransactionDto;
import com.finance.aiservice.util.DataSanitizer;
import com.finance.aiservice.util.DateRangeParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI Function Tool: Get Transaction History
 *
 * Enables AI to query specific transactions for questions like:
 * - "Show me my recent dining expenses"
 * - "What did I buy yesterday?"
 * - "List my last 10 transactions"
 *
 * SECURITY: Sensitive fields (account numbers, merchant names) are masked before returning to AI.
 */
@Slf4j
@Component("getTransactionHistory")
@Description("""
    Get user's individual transaction details (not totals). Results are automatically sorted for you! Supports date range and category filtering.

    USE THIS WHEN user asks about:
    - "What is my biggest/largest/most expensive transaction?" → sortBy="amountOut", sortDirection="DESC"
    - "What is my smallest/least expensive transaction?" → sortBy="amountOut", sortDirection="ASC"
    - "Show me my recent transactions" → sortBy="transactionDate", sortDirection="DESC"
    - "Show me my oldest transactions" → sortBy="transactionDate", sortDirection="ASC"
    - "List my expenses/income" → sortBy="transactionDate"
    - "Chi tiết giao dịch" (transaction details)
    - "Giao dịch lớn nhất" / "khoản chi lớn nhất" (largest transaction) → sortBy="amountOut", sortDirection="DESC"
    - "Giao dịch ít nhất" / "khoản chi ít nhất" / "giao dịch nhỏ nhất" / "khoản chi nhỏ nhất" (smallest transaction) → sortBy="amountOut", sortDirection="ASC"
    - "Giao dịch gần đây" (recent transactions)

    DATE FILTERING:
    - "Chi tháng này" (this month) → dateRange="tháng này"
    - "Chi tháng trước" (last month) → dateRange="tháng trước"
    - "Chi tháng 12" (December) → dateRange="tháng 12"
    - "Chi 3 tháng gần đây" (last 3 months) → dateRange="3 tháng gần đây"
    - "Chi tuần này" (this week) → dateRange="tuần này"
    - "Chi năm nay" (this year) → dateRange="năm nay"

    CATEGORY FILTERING - 🚨 CRITICAL WORKFLOW:

    BEFORE using category parameter, you MUST:
    1. Call getUserCategories(userId) to get exact category names
    2. Match user's query to exact name from the list
    3. Use that exact name in category parameter

    ❌ WRONG: category="Shopping" (guessed)
    ✅ CORRECT: Call getUserCategories first, then use exact name from list

    Example:
    User: "Cho tôi xem chi tiêu mua sắm"
    Step 1: getUserCategories(userId) → ["Ăn uống", "Mua sắm", "Giao thông"]
    Step 2: Match "mua sắm" → "Mua sắm"
    Step 3: getTransactionHistory(userId, category="Mua sắm", limit=10)

    DO NOT USE for total amounts - use getUserSpending instead.

    Parameters:
    - userId: Required - User ID from context
    - dateRange: Optional - Vietnamese date expression (e.g., "tháng này", "tháng 12", "3 tháng gần đây")
    - category: Optional - Category to filter by (e.g., "Ăn uống", "Shopping", "Giải trí")
    - sortBy: Optional - Field to sort by:
      * "amountOut" - Sort by expense amount
      * "amountIn" - Sort by income amount
      * "transactionDate" - Sort by date (default)
    - sortDirection: Optional - Sort direction:
      * "DESC" - Largest/most recent first (default)
      * "ASC" - Smallest/oldest first
    - limit: Optional - Max transactions to return (default: 10, max: 50)

    IMPORTANT:
    - For "largest/biggest" → sortDirection="DESC" (default)
    - For "smallest/least" → sortDirection="ASC"

    Examples:
    - "Biggest expense this month" → getTransactionHistory(userId="1", dateRange="tháng này", sortBy="amountOut", sortDirection="DESC", limit=1)
    - "Smallest dining expense last month" → getTransactionHistory(userId="1", dateRange="tháng trước", category="Ăn uống", sortBy="amountOut", sortDirection="ASC", limit=1)
    - "Recent shopping transactions" → getTransactionHistory(userId="1", category="Shopping", sortBy="transactionDate", sortDirection="DESC", limit=10)
    """)
@RequiredArgsConstructor
public class GetTransactionHistoryTool implements Function<GetTransactionHistoryTool.Request, GetTransactionHistoryTool.Response> {

    private final FinanceServiceClient financeServiceClient;
    private final DataSanitizer dataSanitizer;
    private final DateRangeParser dateRangeParser;

    @Override
    public Response apply(Request request) {
        log.info("AI Tool called: getTransactionHistory for user {} (dateRange: {}, category: {}, sortBy: {}, sortDirection: {}, limit: {})",
            request.userId(), request.dateRange(), request.category(), request.sortBy(), request.sortDirection(), request.limit());

        try {
            int limit = request.limit() != null ? request.limit() : 10;  // Default: last 10 transactions

            // Cap the limit to prevent overwhelming the AI model
            if (limit > 50) {
                limit = 50;
            }

            String sortBy = request.sortBy() != null ? request.sortBy() : "transactionDate";
            String sortDirection = request.sortDirection() != null ? request.sortDirection() : "DESC";

            // Parse date range if provided
            String startDate = null;
            String endDate = null;
            if (request.dateRange() != null && !request.dateRange().trim().isEmpty()) {
                DateRangeParser.DateRange dateRange = dateRangeParser.parse(request.dateRange());
                startDate = dateRange.startDate();
                endDate = dateRange.endDate();
                log.info("Parsed date range '{}' to startDate: {}, endDate: {}", request.dateRange(), startDate, endDate);
            }

            // Check if we're in group mode (bankAccountIds available in RequestContext)
            List<String> groupBankAccountIds = RequestContext.getBankAccountIds();
            boolean isGroupMode = RequestContext.hasGroupBankAccounts();

            if (isGroupMode) {
                log.info("Group mode: querying transactions with bankAccountIds={}", groupBankAccountIds.size());
            }

            // Call FinanceService with pagination, sorting, and filters
            // Pass bankAccountIds for group mode (null for personal mode)
            var response = financeServiceClient.getTransactionHistory(
                request.userId(),
                startDate,            // Date filter start
                endDate,              // Date filter end
                null,                 // textSearch (not used for category anymore)
                request.category(),   // Category filter (exact match)
                null,                 // transactionType (null = both income and expense)
                sortBy,               // Sort field
                sortDirection,        // Sort direction (DESC for largest/recent, ASC for smallest/oldest)
                0,                    // First page
                limit,                // Page size
                isGroupMode ? groupBankAccountIds : null  // bankAccountIds for group mode
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return Response.error("Failed to fetch transaction history from FinanceService");
            }

            // Extract transactions from paginated response
            var page = response.getBody();
            List<com.finance.aiservice.dto.TransactionDto> transactions = page.content();

            // Sanitize transactions (decrypt + mask sensitive data)
            // Pass userId to ensure correct decryption key is used
            String userId = request.userId();
            List<SanitizedTransaction> sanitizedTransactions = transactions.stream()
                .map(transaction -> sanitizeTransaction(userId, transaction))
                .collect(Collectors.toList());

            return Response.success(sanitizedTransactions);

        } catch (Exception e) {
            log.error("Error in getTransactionHistory tool: {}", e.getMessage(), e);
            return Response.error("Error retrieving transaction history: " + e.getMessage());
        }
    }

    /**
     * Sanitize transaction data before returning to AI.
     * - Decrypt encrypted fields (if encrypted) using USER's encryption key
     * - Mask account numbers (show only last 4 digits)
     * - Mask merchant names for privacy
     * - Calculate the transaction amount from amountOut/amountIn
     *
     * @param userId User ID - CRITICAL for getting the correct decryption key from KeyManagementService
     * @param transaction Transaction to sanitize
     */
    private SanitizedTransaction sanitizeTransaction(String userId, TransactionDto transaction) {
        String bankBrandName = transaction.bankBrandName();
        String transactionContent = transaction.transactionContent();

        // NOTE: Transaction content is stored as PLAINTEXT in MongoDB, no decryption needed
        // Only bankBrandName might be encrypted in some cases

        // Mask bank/merchant name for privacy (only decrypt if it looks encrypted)
        if (bankBrandName != null && !bankBrandName.isBlank()) {
            // Only try to decrypt if data looks encrypted (starts with special chars or is base64-like)
            if (looksEncrypted(bankBrandName)) {
                try {
                    String decrypted = dataSanitizer.decryptAndMask(userId, bankBrandName);
                    if (decrypted != null && !decrypted.equals(bankBrandName)) {
                        bankBrandName = decrypted;
                    }
                } catch (Exception e) {
                    // Decryption failed, just mask it
                    bankBrandName = dataSanitizer.maskMerchantName(bankBrandName);
                }
            } else {
                // Not encrypted, just mask it for privacy
                bankBrandName = dataSanitizer.maskMerchantName(bankBrandName);
            }
        }

        // Transaction content is PLAINTEXT - use as-is (it contains useful info for AI)
        // Examples: "NGUYEN BINH AN chuyen tien", "Thanh toan hoa don"
        // No decryption or masking needed

        // Account number is already masked by FinanceService (****1234)
        // But double-check for safety
        String accountNumber = transaction.accountNumber();
        if (accountNumber != null && !accountNumber.contains("****")) {
            accountNumber = dataSanitizer.maskAccountNumber(accountNumber);
        }

        // Calculate amount: use amountOut for expenses, amountIn for income
        Long amount = 0L;
        String type = transaction.transactionType();

        if (transaction.amountOut() != null && transaction.amountOut() > 0) {
            amount = transaction.amountOut().longValue();
            type = "EXPENSE";
        } else if (transaction.amountIn() != null && transaction.amountIn() > 0) {
            amount = transaction.amountIn().longValue();
            type = "INCOME";
        }

        return new SanitizedTransaction(
            transaction.id(),
            amount,
            type,
            transaction.category(),
            transactionContent,
            bankBrandName,
            transaction.transactionDate(),
            accountNumber
        );
    }

    /**
     * Check if data looks encrypted (to avoid decrypting plaintext).
     *
     * Heuristic:
     * - Plaintext bank/merchant names are typically short, readable text with spaces
     * - Encrypted data is usually long Base64 strings or binary-looking data
     *
     * @param data String to check
     * @return true if data appears encrypted, false if plaintext
     */
    private boolean looksEncrypted(String data) {
        if (data == null || data.isBlank()) {
            return false;
        }

        // Check for common plaintext patterns (bank names, merchant names)
        // Examples: "BIDV", "Vietcombank", "STARBUCKS", "ATM WITHDRAWAL"
        // These are usually short, readable text with spaces and common punctuation
        if (data.length() < 50 && data.matches("^[a-zA-Z0-9\\s\\-._()&]+$")) {
            // Short alphanumeric string with common punctuation -> likely plaintext
            return false;
        }

        // If it's a long string without spaces, might be Base64-encoded encrypted data
        if (data.length() > 100 && !data.contains(" ")) {
            return true;
        }

        // Check if it looks like Base64 (only A-Za-z0-9+/= characters)
        if (data.matches("^[A-Za-z0-9+/=]+$") && data.length() > 50) {
            return true;
        }

        // Default: assume plaintext for safety (avoid unnecessary decryption attempts)
        return false;
    }

    /**
     * Request DTO for AI function calling.
     */
    public record Request(
        @JsonProperty(required = true)
        @JsonPropertyDescription("User ID to get transactions for")
        String userId,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Vietnamese date expression to filter transactions: 'tháng này' (this month), 'tháng trước' (last month), 'tháng 12' (December), '3 tháng gần đây' (last 3 months), 'tuần này' (this week), 'năm nay' (this year). Leave empty for all time.")
        String dateRange,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Category to filter by (e.g., 'Ăn uống', 'Shopping', 'Giải trí', 'Di chuyển'). Leave empty for all categories.")
        String category,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Field to sort by: 'amountOut' (for expense amount), 'amountIn' (for income amount), 'transactionDate' (for date, default)")
        String sortBy,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Sort direction: 'DESC' for largest/most recent first (default), 'ASC' for smallest/oldest first")
        String sortDirection,

        @JsonProperty(required = false)
        @JsonPropertyDescription("Max number of transactions to return (default: 10, max: 50)")
        Integer limit
    ) {}

    /**
     * Response DTO returned to AI.
     */
    public record Response(
        @JsonProperty("transactions")
        List<SanitizedTransaction> transactions,

        @JsonProperty("count")
        Integer count,

        @JsonProperty("isError")
        Boolean isError,

        @JsonProperty("errorMessage")
        String errorMessage
    ) {
        public static Response success(List<SanitizedTransaction> transactions) {
            return new Response(transactions, transactions.size(), false, null);
        }

        public static Response error(String errorMessage) {
            return new Response(new ArrayList<>(), 0, true, errorMessage);
        }
    }

    /**
     * Sanitized transaction data safe for AI processing.
     */
    public record SanitizedTransaction(
        @JsonProperty("id")
        String id,

        @JsonProperty("amount")
        Long amount,

        @JsonProperty("type")
        String type,

        @JsonProperty("category")
        String category,

        @JsonProperty("description")
        String description,  // Sanitized

        @JsonProperty("merchant")
        String merchant,  // Masked: "ABC***"

        @JsonProperty("date")
        String date,

        @JsonProperty("account")
        String account  // Masked: "****1234"
    ) {}
}
