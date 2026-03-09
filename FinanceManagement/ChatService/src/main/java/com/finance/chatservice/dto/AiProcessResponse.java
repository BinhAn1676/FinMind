package com.finance.chatservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Response DTO from AIService internal API.
 *
 * Returned to ChatService after AI processing.
 * ChatService will save this as a message from AI_BOT_001 and broadcast via WebSocket.
 */
@Builder
public record AiProcessResponse(

    /**
     * AI-generated response text (in Vietnamese/English).
     * This is what the user will see in the chat.
     */
    @JsonProperty("response")
    String response,

    /**
     * AI confidence score (0.0-1.0).
     * Optional - can be used for quality monitoring.
     */
    @JsonProperty("confidence")
    Double confidence,

    /**
     * Processing time in milliseconds.
     * Used for monitoring and SLA tracking.
     */
    @JsonProperty("processingTimeMs")
    Long processingTimeMs,

    /**
     * Error flag - true if this is an error response.
     */
    @JsonProperty("isError")
    Boolean isError,

    /**
     * Error message if processing failed.
     */
    @JsonProperty("errorMessage")
    String errorMessage

) {}
