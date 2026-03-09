package com.finance.chatservice.client;

import com.finance.chatservice.dto.AiProcessRequest;
import com.finance.chatservice.dto.AiProcessResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign Client for AIService integration.
 *
 * ARCHITECTURE:
 * User → Angular Chat → WebSocket → ChatService → (THIS CLIENT) → AIService
 *
 * When ChatService detects a message to AI_BOT_001 (FinBot), it calls AIService
 * to process the message with Ollama AI, then broadcasts the AI's response back
 * to the user via WebSocket.
 *
 * Uses Eureka service discovery - no hardcoded URL.
 * INTERNAL USE ONLY: This client is for backend-to-backend communication.
 * Frontend should never call AIService directly.
 */
@FeignClient(name = "ai")
public interface AiServiceClient {

    /**
     * Call AIService to process message with Ollama AI.
     *
     * @param request AI processing request containing user message
     * @return AI-generated response
     */
    @PostMapping("/api/ai/internal/process")
    ResponseEntity<AiProcessResponse> processMessage(@RequestBody AiProcessRequest request);
}
