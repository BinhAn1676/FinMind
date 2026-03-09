package com.finance.chatservice.dto;

import lombok.Builder;

import java.util.List;

/**
 * Request DTO for AIService internal API.
 *
 * Sent from ChatService to AIService when a message is directed to AI_BOT_001.
 */
@Builder
public record AiProcessRequest(
    /**
     * User ID who sent the message.
     */
    String userId,

    /**
     * The message content to process with AI.
     */
    String message,

    /**
     * Conversation ID for context (group chat or 1:1).
     */
    String conversationId,

    /**
     * User's preferred language for response.
     * Defaults to "vi" (Vietnamese).
     */
    String language,

    /**
     * JWT token for service-to-service authentication.
     * Passed through to AIService for authenticated backend calls.
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
