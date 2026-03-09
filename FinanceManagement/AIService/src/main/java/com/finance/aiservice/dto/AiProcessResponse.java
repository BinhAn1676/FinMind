package com.finance.aiservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

/**
 * Internal response DTO for AI message processing.
 *
 * RETURNED TO: ChatService
 *
 * ChatService receives this response and:
 * 1. Saves it as a message from AI_BOT_001
 * 2. Broadcasts via WebSocket to the user
 * 3. Stores in MongoDB for chat history
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

) {

    /**
     * Factory method for successful AI response.
     */
    public static AiProcessResponse success(String response, long processingTimeMs) {
        return AiProcessResponse.builder()
            .response(response)
            .confidence(null)  // TODO: Extract from AI if available
            .processingTimeMs(processingTimeMs)
            .isError(false)
            .errorMessage(null)
            .build();
    }

    /**
     * Factory method for error response.
     */
    public static AiProcessResponse error(String errorMessage) {
        return AiProcessResponse.builder()
            .response(errorMessage)
            .confidence(null)
            .processingTimeMs(null)
            .isError(true)
            .errorMessage(errorMessage)
            .build();
    }
}
