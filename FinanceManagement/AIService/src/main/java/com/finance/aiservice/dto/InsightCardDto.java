package com.finance.aiservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.io.Serializable;

/**
 * Data contract for AI Insight Cards displayed on the Frontend Dashboard.
 *
 * CRITICAL: This structure MUST match the Angular UI expectations exactly.
 * Any changes require Frontend coordination.
 *
 * UI Usage:
 * - "WARNING" cards: Anomaly alerts, high spending warnings
 * - "SAVING" cards: Optimization suggestions, budget recommendations
 * - "INFO" cards: General financial insights
 *
 * Example JSON:
 * {
 *   "type": "WARNING",
 *   "title": "High Spending Alert",
 *   "amount": 500000,
 *   "message": "Your dining expenses exceeded budget by 25% this week",
 *   "action": "Review Transactions"
 * }
 */
@Builder
public record InsightCardDto(

    /**
     * Card type determines UI styling and icon.
     * - WARNING: Red/orange, alert icon (high spending, anomalies)
     * - SAVING: Green, savings icon (optimization suggestions)
     * - INFO: Blue, info icon (general insights)
     */
    @NotNull
    @JsonProperty("type")
    CardType type,

    /**
     * Card title - short, attention-grabbing headline.
     * Max length: 50 characters for optimal UI display.
     * Examples:
     * - "High Spending Detected"
     * - "Save 500k This Week"
     * - "Budget Goal Achieved"
     */
    @NotBlank
    @JsonProperty("title")
    String title,

    /**
     * Monetary amount relevant to this insight (in VND).
     * Can be:
     * - Overspending amount (WARNING)
     * - Potential savings (SAVING)
     * - Total analyzed amount (INFO)
     *
     * Frontend will format with Vietnamese locale (e.g., 500.000đ)
     */
    @JsonProperty("amount")
    Long amount,

    /**
     * Detailed explanation message from AI.
     * Max length: 200 characters for card display.
     * Should be actionable and specific.
     */
    @NotBlank
    @JsonProperty("message")
    String message,

    /**
     * Call-to-action text for the card button.
     * Examples:
     * - "Limit Now"
     * - "View Details"
     * - "See Transactions"
     * - "Apply Tips"
     *
     * Max length: 20 characters
     */
    @NotBlank
    @JsonProperty("action")
    String action,

    /**
     * Optional: Category of the insight (e.g., "DINING", "TRANSPORT")
     * Used for filtering and grouping in the UI.
     */
    @JsonProperty("category")
    String category,

    /**
     * Optional: Severity level for WARNING cards (1-5)
     * 1 = Minor concern, 5 = Critical issue
     * Used for sorting and prioritization.
     */
    @JsonProperty("severity")
    Integer severity

) implements Serializable {

    public enum CardType {
        @JsonProperty("WARNING")
        WARNING,

        @JsonProperty("SAVING")
        SAVING,

        @JsonProperty("INFO")
        INFO
    }

    /**
     * Factory method for creating warning cards.
     */
    public static InsightCardDto createWarning(
        String title,
        Long amount,
        String message,
        Integer severity
    ) {
        return InsightCardDto.builder()
            .type(CardType.WARNING)
            .title(title)
            .amount(amount)
            .message(message)
            .action("Review Transactions")
            .severity(severity)
            .build();
    }

    /**
     * Factory method for creating saving suggestion cards.
     */
    public static InsightCardDto createSaving(
        String title,
        Long potentialSavings,
        String message
    ) {
        return InsightCardDto.builder()
            .type(CardType.SAVING)
            .title(title)
            .amount(potentialSavings)
            .message(message)
            .action("Apply Tips")
            .severity(null)
            .build();
    }

    /**
     * Factory method for creating info cards.
     */
    public static InsightCardDto createInfo(
        String title,
        Long amount,
        String message,
        String category
    ) {
        return InsightCardDto.builder()
            .type(CardType.INFO)
            .title(title)
            .amount(amount)
            .message(message)
            .action("View Details")
            .category(category)
            .severity(null)
            .build();
    }
}
