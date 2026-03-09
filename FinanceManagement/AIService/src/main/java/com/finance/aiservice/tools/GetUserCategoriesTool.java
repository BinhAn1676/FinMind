package com.finance.aiservice.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.finance.aiservice.client.FinanceServiceClient;
import com.finance.aiservice.dto.CategoryDto;
import com.finance.aiservice.service.CategoryCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Description;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

/**
 * AI Function Tool: Get User Categories
 *
 * Enables AI to retrieve the list of categories that a user has.
 * This is essential before analyzing or filtering transactions by category.
 *
 * Performance Optimization:
 * - Reads directly from Redis cache (fast!)
 * - Falls back to FinanceService if cache miss
 * - FinanceService automatically caches result for next call
 *
 * Use Cases:
 * - "What categories do I have?"
 * - "Show me my spending categories"
 * - Before analyzing a category, check if it exists
 * - Get accurate category names (not guessing)
 */
@Slf4j
@Component("getUserCategories")
@Description("""
    Get list of category names for a user.

    Use when user asks: "danh mục của tôi?", "what categories?", "my categories?"

    Returns list like: ["Ăn uống", "Mua sắm", "Giao thông", ...]

    🛑 Call ONCE then respond in VIETNAMESE. Don't call multiple times!

    Response format example:
    "Bạn có 12 danh mục: Ăn uống, Mua sắm, Giao thông, Giải trí, Sức khỏe,..."
    """)
@RequiredArgsConstructor
public class GetUserCategoriesTool implements Function<GetUserCategoriesTool.Request, GetUserCategoriesTool.Response> {

    private final CategoryCacheService categoryCacheService;
    private final FinanceServiceClient financeServiceClient;

    @Override
    public Response apply(Request request) {
        log.info("AI Tool called: getUserCategories for user {}", request.userId());

        try {
            // Try to read directly from Redis cache (fast!)
            List<CategoryDto> categories = categoryCacheService.getCachedCategories(request.userId());

            // If cache miss, call FinanceService (which will cache the result)
            if (categories == null) {
                log.debug("Cache miss, calling FinanceService for user {}", request.userId());
                ResponseEntity<List<CategoryDto>> response = financeServiceClient.getUserCategories(
                    request.userId()
                );

                if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                    return Response.error("Failed to fetch categories from FinanceService");
                }

                categories = response.getBody();
                log.info("Fetched {} categories from FinanceService for user {} (now cached)", categories.size(), request.userId());
            } else {
                log.info("Found {} categories from Redis cache for user {}", categories.size(), request.userId());
            }

            return Response.success(categories);

        } catch (Exception e) {
            log.error("Error in getUserCategories tool: {}", e.getMessage(), e);
            return Response.error("Error fetching categories: " + e.getMessage());
        }
    }

    /**
     * Request DTO for AI function calling.
     */
    public record Request(
        @JsonProperty(required = true)
        @JsonPropertyDescription("User ID to get categories for")
        String userId
    ) {}

    /**
     * Response DTO returned to AI with list of categories.
     */
    public record Response(
        @JsonProperty("success")
        boolean success,

        @JsonProperty("categories")
        List<CategoryDto> categories,

        @JsonProperty("categoryCount")
        Integer categoryCount,

        @JsonProperty("errorMessage")
        String errorMessage
    ) {
        public static Response success(List<CategoryDto> categories) {
            return new Response(true, categories, categories.size(), null);
        }

        public static Response error(String errorMessage) {
            return new Response(false, List.of(), 0, errorMessage);
        }
    }
}
