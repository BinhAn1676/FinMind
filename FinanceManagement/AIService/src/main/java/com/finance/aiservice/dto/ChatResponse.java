package com.finance.aiservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Chat response DTO sent to frontend.
 */
@Builder
public record ChatResponse(

    /**
     * AI's response message.
     */
    @JsonProperty("message")
    String message,

    /**
     * Bot user ID (for chat UI to display sender).
     */
    @JsonProperty("botUserId")
    String botUserId,

    /**
     * Timestamp of response.
     */
    @JsonProperty("timestamp")
    LocalDateTime timestamp,

    /**
     * Optional suggested follow-up actions/questions.
     * Frontend can display as quick-reply buttons.
     */
    @JsonProperty("suggestions")
    List<String> suggestions,

    /**
     * Optional metadata (confidence score, data sources used, etc.)
     */
    @JsonProperty("metadata")
    java.util.Map<String, Object> metadata,

    /**
     * Error flag (true if this is an error response).
     */
    @JsonProperty("isError")
    Boolean isError

) {

    /**
     * Factory method for successful response.
     */
    public static ChatResponse success(String message, List<String> suggestions) {
        return ChatResponse.builder()
            .message(message)
            .botUserId("AI_BOT_001")
            .timestamp(LocalDateTime.now())
            .suggestions(suggestions)
            .isError(false)
            .build();
    }

    /**
     * Factory method for error response.
     */
    public static ChatResponse error(String errorMessage) {
        return ChatResponse.builder()
            .message(errorMessage)
            .botUserId("AI_BOT_001")
            .timestamp(LocalDateTime.now())
            .isError(true)
            .build();
    }
}
