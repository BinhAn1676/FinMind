package com.finance.aiservice.controller;

import com.finance.aiservice.dto.InsightCardDto;
import com.finance.aiservice.service.InsightGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for AI Insight Cards.
 *
 * FRONTEND INTEGRATION:
 * Angular Dashboard calls GET /api/ai/insights/dashboard?userId={id}
 * → Returns List<InsightCardDto>
 * → Angular renders cards in dashboard UI
 *
 * ARCHITECTURE:
 * Frontend → API Gateway → AIService (this controller) → InsightGenerator → FinanceService → AI
 *
 * Features:
 * - Generate AI-powered financial insights
 * - Cached for 24 hours per user
 * - Automatic daily regeneration via scheduled job
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightGenerator insightGenerator;

    /**
     * Get AI-generated insight cards for dashboard.
     *
     * Example Request:
     * GET /api/ai/insights/dashboard?userId=user-123
     *
     * Example Response:
     * [
     *   {
     *     "type": "WARNING",
     *     "title": "Chi Tiêu Dining Cao",
     *     "amount": 2500000,
     *     "message": "Bạn đã chi 2,5 triệu đồng cho ăn uống tháng này, cao hơn 40% so với tháng trước.",
     *     "action": "Xem Chi Tiết",
     *     "category": "DINING",
     *     "severity": 3
     *   }
     * ]
     *
     * @param userId User ID (from JWT token or query param)
     * @return List of insight cards
     */
    @GetMapping("/dashboard")
    @Cacheable(value = "insightCards", key = "#userId", unless = "#result == null || #result.isEmpty()")
    public ResponseEntity<List<InsightCardDto>> getDashboardInsights(
        @RequestParam("userId") String userId
    ) {
        log.info("Fetching dashboard insights for user {}", userId);

        try {
            // Generate insights (fetches data from FinanceService, processes with AI)
            List<InsightCardDto> insights = insightGenerator.generateInsightsForUser(userId);

            if (insights == null || insights.isEmpty()) {
                log.warn("No insights generated for user {}", userId);
                return ResponseEntity.ok(List.of());
            }

            log.info("Returning {} insights for user {}", insights.size(), userId);
            return ResponseEntity.ok(insights);

        } catch (Exception e) {
            log.error("Error generating insights for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Trigger manual insight regeneration (admin/debug only).
     *
     * POST /api/ai/insights/refresh?userId=user-123
     *
     * @param userId User ID
     * @return Newly generated insights
     */
    @PostMapping("/refresh")
    public ResponseEntity<List<InsightCardDto>> refreshInsights(
        @RequestParam("userId") String userId
    ) {
        log.info("Manual insight refresh requested for user {}", userId);

        try {
            // Force regeneration (bypasses cache)
            List<InsightCardDto> insights = insightGenerator.generateInsightsForUser(userId);

            return ResponseEntity.ok(insights);

        } catch (Exception e) {
            log.error("Error refreshing insights for user {}: {}", userId, e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Health check endpoint for insights API.
     *
     * GET /api/ai/insights/health
     *
     * @return Health status
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Insight API is healthy");
    }
}
