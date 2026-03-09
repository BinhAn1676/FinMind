package com.finance.aiservice.service;

import com.finance.aiservice.context.RequestContext;
import com.finance.aiservice.context.ToolCallGuard;
import com.finance.aiservice.dto.AiProcessRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;

/**
 * Interactive chat service with RAG support.
 *
 * INTERNAL SERVICE - Called by ChatBotController when ChatService makes request.
 *
 * Features:
 * - Conversational AI using Ollama
 * - RAG: Retrieves relevant transaction data from vector store (future)
 * - Function calling: Can query FinanceService for real-time data
 * - Context-aware responses
 */
@Slf4j
@Service
public class InteractiveChatService {

    private final ChatClient chatClient;
    private final JwtDecoder jwtDecoder;

    public InteractiveChatService(
        @Qualifier("conversationalChatClient") ChatClient chatClient,
        JwtDecoder jwtDecoder) {
        this.chatClient = chatClient;
        this.jwtDecoder = jwtDecoder;
    }

    /**
     * Process user message and generate AI response.
     *
     * @param request Request from ChatService
     * @return AI response text (plain string, no JSON)
     */
    public String processMessage(AiProcessRequest request) {
        log.debug("Processing AI message for user {}: {}", request.userId(), request.message());

        // 🔐 Set JWT token in SecurityContext for Feign clients
        if (request.jwtToken() != null && !request.jwtToken().isBlank()) {
            try {
                Jwt jwt = jwtDecoder.decode(request.jwtToken());
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(jwt, null, java.util.Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("✅ Set JWT token in SecurityContext for service-to-service calls");
            } catch (Exception e) {
                log.warn("⚠️ Failed to decode JWT token: {}", e.getMessage());
            }
        } else {
            log.warn("⚠️ No JWT token provided - service-to-service calls may fail");
        }

        // 🔐 Set real userId and user question in context
        RequestContext.setUserId(request.userId());
        RequestContext.setUserQuestion(request.message());
        log.info("Set RequestContext userId={} and question for AI function calls", request.userId());

        // 🏠 Set group context if this is a group chat
        if (request.isGroupChat()) {
            RequestContext.setGroupChat(true);
            RequestContext.setGroupId(request.groupId());
            RequestContext.setGroupName(request.groupName());
            RequestContext.setBankAccountIds(request.bankAccountIds());
            log.info("Set group context: groupId={}, groupName='{}', bankAccountIds={}",
                request.groupId(), request.groupName(),
                request.bankAccountIds() != null ? request.bankAccountIds().size() : 0);
        }

        try {
            // Build contextualized prompt
            String prompt = buildPrompt(request);

            // Call AI with function calling enabled
            String aiResponse = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();

            log.debug("AI response generated: {}", aiResponse);

            // 🎯 FORECAST WORKAROUND: Check if getFinancialForecast was called
            // If it was, use the cached result directly instead of relying on AI's poor response handling
            com.finance.aiservice.tools.GetFinancialForecastTool.Response cachedForecast =
                ToolCallGuard.getCachedResult("getFinancialForecast",
                    com.finance.aiservice.tools.GetFinancialForecastTool.Response.class);

            if (cachedForecast != null && cachedForecast.success()) {
                log.info("🔮 Forecast was called - using cached forecast directly instead of AI response");
                log.debug("Replacing AI response '{}' with cached forecast message", aiResponse);
                return cachedForecast.message();
            }

            return aiResponse;

        } catch (Exception e) {
            log.error("Error processing chat message: {}", e.getMessage(), e);
            return "Xin lỗi, tôi gặp sự cố khi xử lý tin nhắn. Vui lòng thử lại sau.";
        } finally {
            // 🧹 CRITICAL: Always clear ThreadLocal to prevent memory leaks
            RequestContext.clear();
            ToolCallGuard.clear();
            SecurityContextHolder.clearContext();
            log.debug("Cleared RequestContext, ToolCallGuard, and SecurityContext after AI processing");
        }
    }

    /**
     * Build contextualized prompt for AI.
     */
    private String buildPrompt(AiProcessRequest request) {
        StringBuilder prompt = new StringBuilder();

        // CRITICAL: Put userId at the very top in a super visible way
        prompt.append("🚨🚨🚨 CRITICAL CONTEXT 🚨🚨🚨\n");
        prompt.append("USER_ID = \"").append(request.userId()).append("\"\n");
        prompt.append("YOU MUST USE THIS EXACT VALUE FOR **ALL** FUNCTION CALLS!\n");
        prompt.append("DO NOT use \"1\", \"system\", or any hardcoded value!\n");
        prompt.append("ALWAYS use userId=\"").append(request.userId()).append("\"\n");
        prompt.append("\n");

        // GROUP CONTEXT: If this is a group chat, inject group-specific instructions
        if (request.isGroupChat() && request.groupName() != null) {
            prompt.append("=== GROUP CONTEXT ===\n");
            prompt.append("You are chatting in a GROUP: \"").append(request.groupName()).append("\"\n");
            prompt.append("This group has linked bank accounts. All financial data queries will\n");
            prompt.append("automatically return GROUP-LEVEL data (not personal data).\n");
            prompt.append("IMPORTANT RULES for group chat:\n");
            prompt.append("- When reporting finances, say \"nhóm\" (group) instead of \"bạn\" (you).\n");
            prompt.append("- Example: \"Tháng này nhóm đã chi 50.000.000 đ\" NOT \"Tháng này bạn đã chi...\"\n");
            prompt.append("- The data returned by tools is GROUP data from all linked bank accounts.\n");
            prompt.append("- Still call the same tools (getUserSpending, analyzeCategory, etc.) - \n");
            prompt.append("  they will automatically use group bank accounts.\n");
            prompt.append("\n");
        }

        // Add user message
        prompt.append("=== USER QUESTION ===\n");
        prompt.append(request.message()).append("\n");
        prompt.append("\n");

        // Check if this is a greeting or capability question
        // Use strict matching - ONLY match if the message is PURELY a greeting/question, not a data query
        String messageLower = request.message().toLowerCase().trim();

        // Greeting: ONLY if message starts with greeting words
        boolean isGreeting = messageLower.matches("^(xin chào|chào bạn|chào|hello|hi|hey|good morning|chào buổi sáng|bạn khỏe không|bạn khỏe).*")
                          && !messageLower.contains("chi tiêu")
                          && !messageLower.contains("thu nhập")
                          && !messageLower.contains("tổng")
                          && !messageLower.contains("giao dịch");

        // Capability question: ONLY if asking what bot can do, without mentioning data
        boolean isCapabilityQuestion = (messageLower.matches(".*(giúp gì|làm được gì|tính năng|bạn là ai|what can you|what are you|who are you).*")
                                     || messageLower.equals("help me with"))
                                    && !messageLower.contains("chi tiêu")
                                    && !messageLower.contains("thu nhập")
                                    && !messageLower.contains("tổng")
                                    && !messageLower.contains("giao dịch")
                                    && !messageLower.contains("danh mục");

        // Thanks/Bye: Simple and safe
        boolean isThanksOrBye = messageLower.matches("^(cảm ơn|thank you|thanks|bye|goodbye|tạm biệt).*");

        if (isGreeting || isCapabilityQuestion || isThanksOrBye) {
            // For greetings/capability questions - NO aggressive function calling instructions
            prompt.append("=== YOUR TASK ===\n");
            prompt.append("This is a ").append(isGreeting ? "greeting" : isCapabilityQuestion ? "capability question" : "thank you/goodbye").append(".\n");
            prompt.append("Respond warmly and conversationally in ").append(request.language().equals("vi") ? "Vietnamese" : "English").append(".\n");
            prompt.append("DO NOT CALL ANY FUNCTIONS for this message!\n");
        } else {
            // For data queries - add aggressive function calling instructions
            prompt.append("=== YOUR TASK ===\n");

            // Detect if this is a forecasting query
            boolean isForecastQuery = messageLower.matches(".*(xu hướng|tương lai|dự đoán|dự báo|tháng sau|tháng tới|sẽ|trend|forecast|future|predict).*")
                                   || (messageLower.contains("cảnh báo") && messageLower.contains("dòng tiền"))
                                   || (messageLower.contains("warning") && messageLower.contains("cash flow"));

            if (isForecastQuery) {
                prompt.append("🔮 USER IS ASKING ABOUT FUTURE/TREND! Use getFinancialForecast!\n");
            }

            prompt.append("1. IMMEDIATELY call the appropriate function with userId=\"").append(request.userId()).append("\"\n");
            prompt.append("2. Call the function ONLY ONCE - never make multiple calls for the same question!\n");
            prompt.append("3. For 'lớn nhất' or 'nhỏ nhất', use limit=1\n");
            prompt.append("4. For 'giao dịch' or list queries, use limit=10\n");
            prompt.append("5. For 'bao nhiêu' or 'tổng chi', use getUserSpending\n");

            if (isForecastQuery) {
                prompt.append("6. For 'xu hướng' or 'tương lai' or 'dự đoán', use getFinancialForecast\n");
            }

            prompt.append("7. Respond in ").append(request.language().equals("vi") ? "Vietnamese" : "English").append("\n");
            prompt.append("\n");

            prompt.append("🚨 CRITICAL RULES 🚨\n");
            prompt.append("- Call function ONCE only!\n");
            prompt.append("- DO NOT make multiple calls!\n");
            prompt.append("- userId MUST be: \"").append(request.userId()).append("\"\n");
            prompt.append("- DO NOT respond with text before calling function!\n");
        }

        return prompt.toString();
    }
}
