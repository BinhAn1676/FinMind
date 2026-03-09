package com.finance.chatservice.controller;

import com.finance.chatservice.dto.ChatMessageDto;
import com.finance.chatservice.dto.SendMessageRequest;
import com.finance.chatservice.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle sending messages via WebSocket
     * Client sends to: /app/chat/{roomId}/send
     * Subscribers receive from: /topic/chat/{roomId}
     * Also sends notifications to all room members via /user/{userId}/queue/chat/notifications
     */
    @MessageMapping("/chat/{roomId}/send")
    public void sendMessage(
            @DestinationVariable String roomId,
            @Payload SendMessageRequest request,
            SimpMessageHeaderAccessor headerAccessor) {
        log.info("WebSocket: Received message for room {} from user {}", roomId, request.getSenderId());

        request.setChatRoomId(roomId);

        // Extract JWT token from WebSocket session attributes
        String jwtToken = com.finance.chatservice.common.config.WebSocketAuthInterceptor
            .getJwtTokenFromSession(headerAccessor.getSessionAttributes());

        if (jwtToken != null) {
            log.debug("✅ WebSocket: Extracted JWT token from session (length: {})", jwtToken.length());
        } else {
            log.warn("⚠️ WebSocket: No JWT token found in session attributes");
        }

        // Pass JWT token through request for AI processing
        request.setJwtToken(jwtToken);

        ChatMessageDto message = chatService.sendMessage(request);
        
        // Broadcast to all subscribers of this room (for open chat boxes)
        messagingTemplate.convertAndSend("/topic/chat/" + roomId, message);
        log.info("WebSocket: Message broadcast to /topic/chat/{}", roomId);
        
        // Send notification to all room members (for unread count badges)
        notifyRoomMembers(roomId, message);
    }
    
    /**
     * Send notification to all members of a room about new message
     * This ensures users receive unread count updates even if they don't have the chat box open
     */
    private void notifyRoomMembers(String roomId, ChatMessageDto message) {
        try {
            // Get member IDs from the room
            var memberIds = chatService.getRoomMemberIds(roomId);
            
            if (memberIds.isEmpty()) {
                log.warn("Cannot notify members: room {} has no members", roomId);
                return;
            }
            
            // Send notification to each member (except sender)
            for (Long memberId : memberIds) {
                if (!memberId.equals(message.getSenderId())) {
                    var notification = Map.of(
                            "type", "NEW_MESSAGE",
                            "roomId", roomId,
                            "senderId", message.getSenderId(),
                            "message", message
                    );
                    
                    // Use topic-based approach for user notifications
                    messagingTemplate.convertAndSend(
                            "/topic/chat/user/" + memberId + "/notifications",
                            notification
                    );
                    log.debug("Sent notification to user {} for new message in room {}", memberId, roomId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to notify room members: {}", e.getMessage());
        }
    }

    /**
     * Handle typing indicator
     * Client sends to: /app/chat/{roomId}/typing
     * Subscribers receive from: /topic/chat/{roomId}/typing
     */
    @MessageMapping("/chat/{roomId}/typing")
    public void handleTyping(
            @DestinationVariable String roomId,
            @Payload Map<String, Object> payload) {
        Long userId = ((Number) payload.get("userId")).longValue();
        String userName = (String) payload.get("userName");
        Boolean isTyping = (Boolean) payload.get("isTyping");
        
        log.debug("WebSocket: User {} is {} in room {}", userName, isTyping ? "typing" : "stopped typing", roomId);
        
        // Broadcast typing status to room
        messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/typing", Map.of(
                "userId", userId,
                "userName", userName,
                "isTyping", isTyping
        ));
    }

    /**
     * Handle read receipts
     * Client sends to: /app/chat/{roomId}/read
     * Subscribers receive from: /topic/chat/{roomId}/read
     */
    @MessageMapping("/chat/{roomId}/read")
    public void handleReadReceipt(
            @DestinationVariable String roomId,
            @Payload Map<String, Object> payload) {
        Long userId = ((Number) payload.get("userId")).longValue();
        
        log.debug("WebSocket: User {} marked messages as read in room {}", userId, roomId);
        
        chatService.markMessagesAsRead(roomId, userId);
        
        // Broadcast read receipt to room
        messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/read", Map.of(
                "userId", userId,
                "roomId", roomId
        ));
    }

    /**
     * Handle user online status
     * Client sends to: /app/chat/online
     * Subscribers receive from: /topic/chat/online
     */
    @MessageMapping("/chat/online")
    public void handleOnlineStatus(@Payload Map<String, Object> payload) {
        Long userId = ((Number) payload.get("userId")).longValue();
        Boolean isOnline = (Boolean) payload.get("isOnline");
        
        log.debug("WebSocket: User {} is now {}", userId, isOnline ? "online" : "offline");
        
        // Broadcast online status
        messagingTemplate.convertAndSend("/topic/chat/online", Map.of(
                "userId", userId,
                "isOnline", isOnline
        ));
    }

    /**
     * Handle message deletion notification
     * Client sends to: /app/chat/{roomId}/delete
     * Subscribers receive from: /topic/chat/{roomId}/delete
     */
    @MessageMapping("/chat/{roomId}/delete")
    public void handleMessageDelete(
            @DestinationVariable String roomId,
            @Payload Map<String, Object> payload) {
        String messageId = (String) payload.get("messageId");
        Long userId = ((Number) payload.get("userId")).longValue();
        
        log.info("WebSocket: User {} deleted message {} in room {}", userId, messageId, roomId);
        
        chatService.deleteMessage(messageId);
        
        // Broadcast deletion to room
        messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/delete", Map.of(
                "messageId", messageId,
                "deletedBy", userId
        ));
    }

    /**
     * Handle message edit notification
     * Client sends to: /app/chat/{roomId}/edit
     * Subscribers receive from: /topic/chat/{roomId}/edit
     */
    @MessageMapping("/chat/{roomId}/edit")
    public void handleMessageEdit(
            @DestinationVariable String roomId,
            @Payload Map<String, Object> payload) {
        String messageId = (String) payload.get("messageId");
        String newContent = (String) payload.get("content");
        Long userId = ((Number) payload.get("userId")).longValue();
        
        log.info("WebSocket: User {} edited message {} in room {}", userId, messageId, roomId);
        
        ChatMessageDto updatedMessage = chatService.updateMessage(messageId, newContent);
        
        // Broadcast edit to room
        messagingTemplate.convertAndSend("/topic/chat/" + roomId + "/edit", updatedMessage);
    }
}
