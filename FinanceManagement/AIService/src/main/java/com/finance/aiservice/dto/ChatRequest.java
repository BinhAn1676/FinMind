package com.finance.aiservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.Map;

/**
 * Chat request DTO from frontend.
 */
@Builder
public record ChatRequest(

    @NotBlank(message = "User ID is required")
    String userId,

    @NotBlank(message = "Message is required")
    String message,

    /**
     * Optional context for the conversation:
     * - conversationId: Thread/group chat ID
     * - language: "vi" or "en"
     * - replyToMessageId: If replying to specific message
     */
    Map<String, String> context

) {}
