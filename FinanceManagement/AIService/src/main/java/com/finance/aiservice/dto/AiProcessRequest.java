package com.finance.aiservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.List;

/**
 * Internal request DTO for AI message processing.
 *
 * CALLER: ChatService (via Feign Client)
 *
 * This DTO carries the minimum information needed for AI processing.
 * ChatService handles all WebSocket, persistence, and broadcasting logic.
 *
 * Group context fields are populated when the message comes from a group chat
 * with @AI mention, allowing the AI to analyze group-linked financial data.
 */
@Builder
public record AiProcessRequest(

    /**
     * User ID who sent the message.
     */
    @NotBlank(message = "User ID is required")
    String userId,

    /**
     * The message content to process with AI.
     */
    @NotBlank(message = "Message is required")
    String message,

    /**
     * Conversation ID for context (group chat or 1:1).
     * Optional - can be null for standalone questions.
     */
    String conversationId,

    /**
     * User's preferred language for response.
     * Defaults to "vi" (Vietnamese).
     */
    String language,

    /**
     * JWT token for service-to-service authentication.
     * This token is passed through to backend services (FinanceService, UserService).
     */
    String jwtToken,

    // ── Group context fields (null/empty for 1-on-1 bot chat) ──

    /**
     * Whether this message comes from a group chat (true) or 1-on-1 bot chat (false).
     */
    boolean isGroupChat,

    /**
     * Group ID from UserService. Null for 1-on-1 bot chats.
     */
    Long groupId,

    /**
     * Group display name for AI context. Null for 1-on-1 bot chats.
     */
    String groupName,

    /**
     * Bank account IDs linked to the group.
     * AI tools use these to query group-level transactions from FinanceService.
     * Null or empty for 1-on-1 bot chats.
     */
    List<String> bankAccountIds

) {
    /**
     * Default language to Vietnamese if not specified.
     */
    public String language() {
        return language != null ? language : "vi";
    }
}
