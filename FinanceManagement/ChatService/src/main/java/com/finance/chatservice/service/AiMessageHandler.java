package com.finance.chatservice.service;

import com.finance.chatservice.client.AiServiceClient;
import com.finance.chatservice.dto.AiProcessRequest;
import com.finance.chatservice.dto.AiProcessResponse;
import com.finance.chatservice.entity.ChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for handling AI bot messages in ChatService.
 *
 * RESPONSIBILITY:
 * - Detect when messages are directed to AI_BOT_001 (FinBot)
 * - Call AIService via Feign client
 * - Convert AI responses into chat messages
 * - Handle errors gracefully
 *
 * ARCHITECTURE:
 * User sends message → WebSocket Controller → AiMessageHandler (this) → AIService
 *                                                     ↓
 * User receives response ← WebSocket broadcast ← Save message ← AIService response
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiMessageHandler {

    private static final Long AI_BOT_SENDER_ID = 999999L;  // AI Bot's sender ID
    private static final String AI_BOT_USERNAME = "FinBot";
    private static final String AI_BOT_AVATAR = "/assets/avatar/finbot.png";

    private final AiServiceClient aiServiceClient;

    /**
     * Check if a message is directed to the AI bot.
     *
     * @param message The chat message to check
     * @return true if message should be processed by AI
     */
    public boolean isAiMessage(ChatMessage message) {
        // Check if message has AI mention flag
        if (Boolean.TRUE.equals(message.getMentionsAI())) {
            return true;
        }

        // @AI mention in message content
        if (message.getContent() != null && message.getContent().contains("@AI")) {
            return true;
        }

        // @FinBot mention
        if (message.getContent() != null && message.getContent().contains("@FinBot")) {
            return true;
        }

        return false;
    }

    /**
     * Process message with AI and return AI's response as a chat message.
     * Supports both personal (1-on-1 bot) and group chat contexts.
     *
     * @param userMessage The user's message to the AI bot
     * @param jwtToken JWT token for service-to-service authentication
     * @param isGroupChat Whether this is a group chat context
     * @param groupId Group ID (null for 1-on-1 bot chat)
     * @param groupName Group display name (null for 1-on-1 bot chat)
     * @param bankAccountIds Bank account IDs linked to the group (null/empty for 1-on-1)
     * @return AI's response message
     */
    public ChatMessage processAiMessage(ChatMessage userMessage, String jwtToken,
                                         boolean isGroupChat, Long groupId,
                                         String groupName, List<String> bankAccountIds) {
        log.info("Processing AI message from user {} in chat room {} (groupChat={}, groupId={})",
            userMessage.getSenderId(), userMessage.getChatRoomId(), isGroupChat, groupId);

        try {
            // Clean message content (remove @AI or @FinBot mentions)
            String cleanedMessage = cleanMessageContent(userMessage.getContent());

            // JWT token logging
            log.info("JWT token for AI service call: {}", jwtToken != null && !jwtToken.isBlank() ? "present (length: " + jwtToken.length() + ")" : "null");

            // Build request for AIService with group context
            AiProcessRequest request = AiProcessRequest.builder()
                .userId(String.valueOf(userMessage.getSenderId()))
                .message(cleanedMessage)
                .conversationId(userMessage.getChatRoomId())
                .language("vi")
                .jwtToken(jwtToken)
                .isGroupChat(isGroupChat)
                .groupId(groupId)
                .groupName(groupName)
                .bankAccountIds(bankAccountIds)
                .build();

            if (isGroupChat) {
                log.info("Group AI request: groupId={}, groupName='{}', bankAccountIds={}",
                    groupId, groupName, bankAccountIds != null ? bankAccountIds.size() : 0);
            }

            // Call AIService via Feign
            ResponseEntity<AiProcessResponse> response = aiServiceClient.processMessage(request);

            // Check response
            if (response.getStatusCode().is2xxSuccessful()
                && response.getBody() != null
                && !Boolean.TRUE.equals(response.getBody().isError())) {

                log.info("AI response received in {}ms",
                    response.getBody().processingTimeMs());

                // Create AI response message
                return createAiResponseMessage(
                    userMessage,
                    response.getBody().response()
                );

            } else {
                // AIService returned error
                String errorMsg = response.getBody() != null
                    ? response.getBody().errorMessage()
                    : "Unknown error";
                log.warn("AIService returned error: {}", errorMsg);
                return createErrorMessage(userMessage);
            }

        } catch (Exception e) {
            log.error("Error calling AIService: {}", e.getMessage(), e);
            return createErrorMessage(userMessage);
        }
    }

    /**
     * Clean message content by removing @AI or @FinBot mentions.
     */
    private String cleanMessageContent(String content) {
        if (content == null) {
            return "";
        }

        return content
            .replaceAll("@AI\\s*", "")
            .replaceAll("@FinBot\\s*", "")
            .trim();
    }

    /**
     * Create AI response message.
     */
    private ChatMessage createAiResponseMessage(ChatMessage originalMessage, String aiResponse) {
        return ChatMessage.builder()
            .chatRoomId(originalMessage.getChatRoomId())
            .senderId(AI_BOT_SENDER_ID)
            .senderName(AI_BOT_USERNAME)
            .senderAvatar(AI_BOT_AVATAR)
            .content(aiResponse)
            .messageType("TEXT")
            .replyToMessageId(originalMessage.getId())
            .replyToContent(originalMessage.getContent())
            .mentionsAI(false)  // This is FROM AI, not TO AI
            .isDeleted(false)
            .isEdited(false)
            .build();
    }

    /**
     * Create error message when AI processing fails.
     */
    private ChatMessage createErrorMessage(ChatMessage originalMessage) {
        return createAiResponseMessage(
            originalMessage,
            "Xin lỗi, tôi gặp sự cố khi xử lý tin nhắn của bạn. Vui lòng thử lại sau. 🙏"
        );
    }
}
