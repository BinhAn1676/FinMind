package com.finance.aiservice.controller;

import com.finance.aiservice.dto.AiProcessRequest;
import com.finance.aiservice.dto.AiProcessResponse;
import com.finance.aiservice.service.InteractiveChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal REST Controller for AI Bot processing.
 *
 * IMPORTANT: This is an INTERNAL API called by ChatService only.
 * Frontend does NOT call this directly.
 *
 * Architecture:
 * User → Angular Chat → WebSocket → ChatService (detects AI_BOT_001)
 *                                         ↓
 *                          Feign Client → AIService /api/ai/internal/process
 *                                         ↓
 *                                   Ollama generates response
 *                                         ↓
 * User ← Angular Chat ← WebSocket ← ChatService ← AIService returns text
 *
 * The AI bot (FinBot / AI_BOT_001) appears as a regular user in ChatService.
 * Users chat with it via existing WebSocket infrastructure - no changes to Angular.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai/internal")
@RequiredArgsConstructor
public class ChatBotController {

    private final InteractiveChatService chatService;

    /**
     * Process a message with AI and return response.
     *
     * CALLER: ChatService (via Feign Client)
     *
     * Flow:
     * 1. ChatService receives message for AI_BOT_001 via WebSocket
     * 2. ChatService calls this endpoint with message content
     * 3. AIService processes with Ollama (using RAG if needed)
     * 4. Returns AI response as plain text
     * 5. ChatService saves response as message from AI_BOT_001
     * 6. ChatService broadcasts via WebSocket to user
     *
     * Example Request:
     * {
     *   "userId": "user-12345",
     *   "message": "Tôi đã chi bao nhiêu tiền cho đi ăn tuần này?",
     *   "conversationId": "conv-abc",
     *   "language": "vi"
     * }
     *
     * Example Response:
     * {
     *   "response": "Tuần này bạn đã chi 850.000đ cho ăn uống 💰",
     *   "confidence": 0.95,
     *   "processingTimeMs": 1250
     * }
     */
    @PostMapping("/process")
    public ResponseEntity<AiProcessResponse> processMessage(
        @Valid @RequestBody AiProcessRequest request
    ) {
        log.info("[INTERNAL] AI processing request from ChatService - userId: {}, conversationId: {}",
            request.userId(), request.conversationId());

        try {
            // Validate request
            if (request.message() == null || request.message().isBlank()) {
                return ResponseEntity.badRequest()
                    .body(AiProcessResponse.error("Message cannot be empty"));
            }

            long startTime = System.currentTimeMillis();

            // Process message with AI
            String aiResponse = chatService.processMessage(request);

            long processingTime = System.currentTimeMillis() - startTime;

            // Return response to ChatService
            AiProcessResponse response = AiProcessResponse.success(
                aiResponse,
                processingTime
            );

            log.info("[INTERNAL] AI processing completed in {}ms", processingTime);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("[INTERNAL] Error processing AI message for user {}: {}",
                request.userId(), e.getMessage(), e);

            return ResponseEntity.internalServerError()
                .body(AiProcessResponse.error(
                    "Xin lỗi, tôi gặp sự cố. Vui lòng thử lại sau."
                ));
        }
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("AIService internal API is healthy");
    }
}
