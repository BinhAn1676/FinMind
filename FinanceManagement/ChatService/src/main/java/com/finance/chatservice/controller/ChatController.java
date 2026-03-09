package com.finance.chatservice.controller;

import com.finance.chatservice.dto.*;
import com.finance.chatservice.model.ErrorResponseDto;
import com.finance.chatservice.service.BotChatRoomService;
import com.finance.chatservice.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;
    private final BotChatRoomService botChatRoomService;

    // =============== Chat Room Endpoints ===============

    @PostMapping("/rooms")
    public ResponseEntity<ChatRoomDto> createChatRoom(@Valid @RequestBody CreateChatRoomRequest request) {
        log.info("Creating chat room for group: {}", request.getGroupId());
        ChatRoomDto chatRoom = chatService.createChatRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(chatRoom);
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomDto> getChatRoomById(@PathVariable String roomId) {
        ChatRoomDto chatRoom = chatService.getChatRoomById(roomId);
        return ResponseEntity.ok(chatRoom);
    }

    @GetMapping("/rooms/group/{groupId}")
    public ResponseEntity<ChatRoomDto> getChatRoomByGroupId(@PathVariable Long groupId) {
        ChatRoomDto chatRoom = chatService.getChatRoomByGroupId(groupId);
        return ResponseEntity.ok(chatRoom);
    }

    @GetMapping("/rooms/user/{userId}")
    public ResponseEntity<List<ChatRoomDto>> getUserChatRooms(@PathVariable Long userId) {
        List<ChatRoomDto> chatRooms = chatService.getUserChatRooms(userId);
        return ResponseEntity.ok(chatRooms);
    }

    /**
     * Get or create 1-on-1 chat room with FinBot.
     * This endpoint automatically creates a dedicated chat room with the AI bot for the user.
     */
    @PostMapping("/rooms/user/{userId}/bot")
    public ResponseEntity<ChatRoomDto> getOrCreateBotChatRoom(@PathVariable Long userId) {
        log.info("Getting or creating bot chat room for user {}", userId);
        ChatRoomDto botChatRoom = botChatRoomService.getOrCreateBotChatRoom(userId);
        return ResponseEntity.ok(botChatRoom);
    }

    @GetMapping("/rooms/user/{userId}/paged")
    public ResponseEntity<Page<ChatRoomDto>> getUserChatRoomsPaged(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "lastMessageTime"));
        Page<ChatRoomDto> chatRooms = chatService.getUserChatRoomsPaged(userId, pageable);
        return ResponseEntity.ok(chatRooms);
    }

    @PutMapping("/rooms/{roomId}")
    public ResponseEntity<ChatRoomDto> updateChatRoom(
            @PathVariable String roomId,
            @Valid @RequestBody CreateChatRoomRequest request) {
        ChatRoomDto chatRoom = chatService.updateChatRoom(roomId, request);
        return ResponseEntity.ok(chatRoom);
    }

    @PostMapping("/rooms/{roomId}/members/{userId}")
    public ResponseEntity<Void> addMemberToRoom(
            @PathVariable String roomId,
            @PathVariable Long userId) {
        chatService.addMemberToRoom(roomId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/rooms/{roomId}/members/{userId}")
    public ResponseEntity<Void> removeMemberFromRoom(
            @PathVariable String roomId,
            @PathVariable Long userId) {
        chatService.removeMemberFromRoom(roomId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/rooms/{roomId}")
    public ResponseEntity<Void> deleteChatRoom(@PathVariable String roomId) {
        chatService.deleteChatRoom(roomId);
        return ResponseEntity.noContent().build();
    }

    // =============== Message Endpoints ===============

    @PostMapping("/messages")
    public ResponseEntity<ChatMessageDto> sendMessage(@Valid @RequestBody SendMessageRequest request) {
        log.info("Sending message to room: {}", request.getChatRoomId());
        ChatMessageDto message = chatService.sendMessage(request);
        
        // Broadcast to room subscribers (for open chat boxes)
        messagingTemplate.convertAndSend("/topic/chat/" + request.getChatRoomId(), message);
        
        // Send notification to all room members (for unread count badges)
        notifyRoomMembers(request.getChatRoomId(), message);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(message);
    }
    
    /**
     * Send notification to all members of a room about new message
     */
    private void notifyRoomMembers(String roomId, ChatMessageDto message) {
        try {
            List<Long> memberIds = chatService.getRoomMemberIds(roomId);
            
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

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<Page<ChatMessageDto>> getMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ChatMessageDto> messages = chatService.getMessages(roomId, pageable);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/rooms/{roomId}/messages/recent")
    public ResponseEntity<List<ChatMessageDto>> getRecentMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "50") int limit) {
        List<ChatMessageDto> messages = chatService.getRecentMessages(roomId, limit);
        return ResponseEntity.ok(messages);
    }

    @GetMapping("/messages/{messageId}")
    public ResponseEntity<ChatMessageDto> getMessageById(@PathVariable String messageId) {
        ChatMessageDto message = chatService.getMessageById(messageId);
        return ResponseEntity.ok(message);
    }

    @PutMapping("/messages/{messageId}")
    public ResponseEntity<ChatMessageDto> updateMessage(
            @PathVariable String messageId,
            @RequestBody Map<String, String> body) {
        String newContent = body.get("content");
        ChatMessageDto message = chatService.updateMessage(messageId, newContent);
        return ResponseEntity.ok(message);
    }

    @DeleteMapping("/messages/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable String messageId) {
        chatService.deleteMessage(messageId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{roomId}/read")
    public ResponseEntity<Void> markMessagesAsRead(
            @PathVariable String roomId,
            @RequestParam Long userId) {
        chatService.markMessagesAsRead(roomId, userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/rooms/{roomId}/unread")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @PathVariable String roomId,
            @RequestParam Long userId) {
        long count = chatService.getUnreadCount(roomId, userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    // =============== Sidebar Panel Endpoints ===============

    @GetMapping("/rooms/{roomId}/messages/search")
    public ResponseEntity<Page<ChatMessageDto>> searchMessages(
            @PathVariable String roomId,
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(chatService.searchMessages(roomId, keyword, pageable));
    }

    @GetMapping("/rooms/{roomId}/media")
    public ResponseEntity<Page<ChatMessageDto>> getMediaMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(chatService.getMediaMessages(roomId, pageable));
    }

    @GetMapping("/rooms/{roomId}/files")
    public ResponseEntity<Page<ChatMessageDto>> getFileMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(chatService.getFileMessages(roomId, pageable));
    }

    @GetMapping("/rooms/{roomId}/links")
    public ResponseEntity<Page<ChatMessageDto>> getLinkMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(chatService.getLinkMessages(roomId, pageable));
    }

    // =============== Sync Endpoint ===============

    @PostMapping("/rooms/sync")
    public ResponseEntity<ChatRoomDto> syncChatRoomWithGroup(@RequestBody Map<String, Object> body) {
        Long groupId = ((Number) body.get("groupId")).longValue();
        String groupName = (String) body.get("groupName");
        String avatarFileId = (String) body.get("avatarFileId");
        @SuppressWarnings("unchecked")
        List<Long> memberIds = body.get("memberIds") != null 
                ? ((List<Number>) body.get("memberIds")).stream().map(Number::longValue).toList()
                : null;

        ChatRoomDto chatRoom = chatService.syncChatRoomWithGroup(groupId, groupName, avatarFileId, memberIds);
        return ResponseEntity.ok(chatRoom);
    }

    // =============== Exception Handler ===============

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleException(Exception ex) {
        log.error("Error in ChatController: {}", ex.getMessage(), ex);
        ErrorResponseDto errorResponse = new ErrorResponseDto(
                "/api/v1/chat",
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage(),
                LocalDateTime.now()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
    }
}
