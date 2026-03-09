package com.finance.chatservice.service.impl;

import com.finance.chatservice.client.UserServiceClient;
import com.finance.chatservice.dto.*;
import com.finance.chatservice.entity.ChatMessage;
import com.finance.chatservice.entity.ChatRoom;
import com.finance.chatservice.exception.ResourceNotFoundException;
import com.finance.chatservice.repository.ChatMessageRepository;
import com.finance.chatservice.repository.ChatRoomRepository;
import com.finance.chatservice.service.AiMessageHandler;
import com.finance.chatservice.service.BotChatRoomService;
import com.finance.chatservice.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final AiMessageHandler aiMessageHandler;
    private final SimpMessagingTemplate messagingTemplate;
    private final BotChatRoomService botChatRoomService;
    private final UserServiceClient userServiceClient;
    private final JwtDecoder jwtDecoder;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    @Transactional
    public ChatRoomDto createChatRoom(CreateChatRoomRequest request) {
        log.info("Creating chat room for group: {}", request.getGroupId());
        
        // Check if chat room already exists for this group
        if (chatRoomRepository.existsByGroupId(request.getGroupId())) {
            return getChatRoomByGroupId(request.getGroupId());
        }

        ChatRoom chatRoom = ChatRoom.builder()
                .groupId(request.getGroupId())
                .name(request.getName())
                .avatarFileId(request.getAvatarFileId())
                .memberIds(request.getMemberIds() != null ? request.getMemberIds() : new ArrayList<>())
                .isActive(true)
                .build();

        chatRoom = chatRoomRepository.save(chatRoom);
        log.info("Chat room created with ID: {}", chatRoom.getId());
        
        return mapToDto(chatRoom);
    }

    @Override
    public ChatRoomDto getChatRoomById(String roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatRoom", "id", roomId));
        return mapToDto(chatRoom);
    }

    @Override
    public ChatRoomDto getChatRoomByGroupId(Long groupId) {
        ChatRoom chatRoom = chatRoomRepository.findByGroupId(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatRoom", "groupId", groupId.toString()));
        return mapToDto(chatRoom);
    }

    @Override
    public List<ChatRoomDto> getUserChatRooms(Long userId) {
        // Get or create bot chat room (always appears first)
        ChatRoomDto botChatRoom = botChatRoomService.getOrCreateBotChatRoom(userId);

        // Get user's group chat rooms (excluding bot room to avoid duplicates)
        List<ChatRoom> chatRooms = chatRoomRepository.findRecentChatRoomsByMemberId(userId);
        List<ChatRoomDto> chatRoomDtos = chatRooms.stream()
                .filter(room -> !botChatRoomService.isBotChatRoom(room.getId())) // Filter out bot room
                .map(room -> mapToDtoWithUnreadCount(room, userId))
                .collect(Collectors.toList());

        // Add bot chat room at the beginning
        List<ChatRoomDto> allRooms = new ArrayList<>();
        allRooms.add(botChatRoom);
        allRooms.addAll(chatRoomDtos);

        return allRooms;
    }

    @Override
    public Page<ChatRoomDto> getUserChatRoomsPaged(Long userId, Pageable pageable) {
        Page<ChatRoom> chatRooms = chatRoomRepository.findByMemberIdAndActive(userId, pageable);
        return chatRooms.map(this::mapToDto);
    }

    @Override
    @Transactional
    public ChatRoomDto updateChatRoom(String roomId, CreateChatRoomRequest request) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatRoom", "id", roomId));
        
        if (request.getName() != null) {
            chatRoom.setName(request.getName());
        }
        if (request.getAvatarFileId() != null) {
            chatRoom.setAvatarFileId(request.getAvatarFileId());
        }
        if (request.getMemberIds() != null) {
            chatRoom.setMemberIds(request.getMemberIds());
        }

        chatRoom = chatRoomRepository.save(chatRoom);
        return mapToDto(chatRoom);
    }

    @Override
    @Transactional
    public void addMemberToRoom(String roomId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatRoom", "id", roomId));
        
        if (!chatRoom.getMemberIds().contains(userId)) {
            chatRoom.getMemberIds().add(userId);
            chatRoomRepository.save(chatRoom);
            log.info("Added member {} to chat room {}", userId, roomId);
        }
    }

    @Override
    @Transactional
    public void removeMemberFromRoom(String roomId, Long userId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatRoom", "id", roomId));
        
        chatRoom.getMemberIds().remove(userId);
        chatRoomRepository.save(chatRoom);
        log.info("Removed member {} from chat room {}", userId, roomId);
    }

    @Override
    @Transactional
    public void deleteChatRoom(String roomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatRoom", "id", roomId));
        
        chatRoom.setIsActive(false);
        chatRoomRepository.save(chatRoom);
        log.info("Deleted (deactivated) chat room {}", roomId);
    }

    @Override
    @Transactional
    public ChatMessageDto sendMessage(SendMessageRequest request) {
        log.info("Sending message to chat room: {}", request.getChatRoomId());
        
        // Verify chat room exists
        ChatRoom chatRoom = chatRoomRepository.findById(request.getChatRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("ChatRoom", "id", request.getChatRoomId()));

        ChatMessage message = ChatMessage.builder()
                .chatRoomId(request.getChatRoomId())
                .senderId(request.getSenderId())
                .senderName(request.getSenderName())
                .senderAvatar(request.getSenderAvatar())
                .content(request.getContent())
                .messageType(request.getMessageType() != null ? request.getMessageType() : "TEXT")
                .fileId(request.getFileId())
                .fileName(request.getFileName())
                .fileType(request.getFileType())
                .fileSize(request.getFileSize())
                .replyToMessageId(request.getReplyToMessageId())
                .replyToContent(request.getReplyToContent())
                .mentionsAI(request.getMentionsAI() != null ? request.getMentionsAI() : false)
                .readBy(new ArrayList<>(List.of(request.getSenderId()))) // Sender has read their own message
                .isDeleted(false)
                .isEdited(false)
                .build();

        message = chatMessageRepository.save(message);

        // Update chat room with last message info
        String now = LocalDateTime.now().format(DATE_FORMATTER);
        chatRoom.setLastMessageSenderId(request.getSenderId());
        chatRoom.setLastMessageContent(getLastMessagePreview(request));
        chatRoom.setLastMessageTime(now);
        chatRoom.setLastMessageType(request.getMessageType());
        chatRoomRepository.save(chatRoom);

        log.info("Message sent with ID: {}", message.getId());

        // Check if this message should be processed by AI
        boolean shouldProcessByAi = false;

        // Check if this is a bot chat room - if so, process ALL user messages
        if (botChatRoomService.isBotChatRoom(request.getChatRoomId())) {
            // In bot chat room, process all messages EXCEPT those from the bot itself
            if (!request.getSenderId().equals(botChatRoomService.getFinBotUserId())) {
                shouldProcessByAi = true;
                log.info("Message in bot chat room detected, will process with AI");
            }
        } else if (aiMessageHandler.isAiMessage(message)) {
            // In group chat, only process messages with @AI mentions
            shouldProcessByAi = true;
            log.info("AI mention detected in message {}, processing...", message.getId());
        }

        if (shouldProcessByAi) {
            processAiMessageAsync(message, request.getJwtToken());
        }

        return mapToDto(message);
    }

    /**
     * Process AI message asynchronously to avoid blocking WebSocket response.
     * Resolves group context (bankAccountIds) if the message is from a group chat.
     *
     * @param userMessage The user's message to process
     * @param jwtToken JWT token for authenticated service calls
     */
    private void processAiMessageAsync(ChatMessage userMessage, String jwtToken) {
        // Run in separate thread to avoid blocking
        new Thread(() -> {
            try {
                // Set JWT in SecurityContext for this thread so Feign clients can authenticate
                setupSecurityContext(jwtToken);

                // Send typing indicator to show AI is processing
                sendAiTypingIndicator(userMessage.getChatRoomId(), true);

                // Resolve group context if this is a group chat
                boolean isGroupChat = false;
                Long groupId = null;
                String groupName = null;
                List<String> bankAccountIds = Collections.emptyList();

                ChatRoom room = chatRoomRepository.findById(userMessage.getChatRoomId()).orElse(null);
                if (room != null && room.getGroupId() != null) {
                    isGroupChat = true;
                    groupId = room.getGroupId();
                    groupName = room.getName();
                    bankAccountIds = resolveGroupBankAccountIds(groupId, jwtToken);
                    log.info("Group AI context resolved: groupId={}, groupName='{}', bankAccountIds={}",
                        groupId, groupName, bankAccountIds.size());
                }

                // Get AI response with group context
                ChatMessage aiResponse = aiMessageHandler.processAiMessage(
                    userMessage, jwtToken, isGroupChat, groupId, groupName, bankAccountIds);

                // Stop typing indicator
                sendAiTypingIndicator(userMessage.getChatRoomId(), false);

                if (aiResponse != null) {
                    // Save AI response
                    aiResponse = chatMessageRepository.save(aiResponse);
                    log.info("AI response saved with ID: {}", aiResponse.getId());

                    // Update chat room with AI's message
                    ChatRoom chatRoom = chatRoomRepository.findById(aiResponse.getChatRoomId()).orElse(null);
                    if (chatRoom != null) {
                        String now = LocalDateTime.now().format(DATE_FORMATTER);
                        chatRoom.setLastMessageSenderId(aiResponse.getSenderId());
                        chatRoom.setLastMessageContent(getPreview(aiResponse.getContent()));
                        chatRoom.setLastMessageTime(now);
                        chatRoom.setLastMessageType("TEXT");
                        chatRoomRepository.save(chatRoom);
                    }

                    // Broadcast AI response via WebSocket
                    ChatMessageDto aiResponseDto = mapToDto(aiResponse);
                    messagingTemplate.convertAndSend(
                        "/topic/chat/" + aiResponse.getChatRoomId(),
                        aiResponseDto
                    );
                    log.info("AI response broadcast to /topic/chat/{}", aiResponse.getChatRoomId());
                }
            } catch (Exception e) {
                log.error("Failed to process AI message: {}", e.getMessage(), e);
                // Stop typing indicator on error
                sendAiTypingIndicator(userMessage.getChatRoomId(), false);
            } finally {
                // Clear SecurityContext to prevent memory leaks in this thread
                SecurityContextHolder.clearContext();
            }
        }).start();
    }

    /**
     * Set up SecurityContext with JWT token for the current thread.
     * Required for Feign client authentication (FeignAuthConfig reads from SecurityContext).
     */
    private void setupSecurityContext(String jwtToken) {
        if (jwtToken != null && !jwtToken.isBlank()) {
            try {
                Jwt jwt = jwtDecoder.decode(jwtToken);
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(jwt, null, java.util.Collections.emptyList());
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Set JWT in SecurityContext for async thread Feign calls");
            } catch (Exception e) {
                log.warn("Failed to decode JWT for SecurityContext: {}", e.getMessage());
            }
        }
    }

    /**
     * Send typing indicator to show AI is processing or finished.
     */
    private void sendAiTypingIndicator(String chatRoomId, boolean isTyping) {
        try {
            var typingIndicator = java.util.Map.of(
                "type", "AI_TYPING",
                "chatRoomId", chatRoomId,
                "senderId", botChatRoomService.getFinBotUserId(),
                "senderName", "FinBot",
                "isTyping", isTyping,
                "message", isTyping ? "FinBot đang soạn tin..." : ""
            );

            messagingTemplate.convertAndSend(
                "/topic/chat/" + chatRoomId + "/typing",
                typingIndicator
            );
            log.debug("Sent AI typing indicator (isTyping={}) to room {}", isTyping, chatRoomId);
        } catch (Exception e) {
            log.error("Failed to send typing indicator: {}", e.getMessage());
        }
    }

    /**
     * Resolve bank account IDs linked to a group by calling UserService.
     *
     * @param groupId The group ID to resolve accounts for
     * @param jwtToken JWT token for authenticated UserService call
     * @return List of bankAccountId strings, or empty list if resolution fails
     */
    @SuppressWarnings("unchecked")
    private List<String> resolveGroupBankAccountIds(Long groupId, String jwtToken) {
        try {
            ResponseEntity<Map<String, Object>> response =
                userServiceClient.getGroupAccounts(groupId, 0, 100);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                List<Map<String, Object>> content = (List<Map<String, Object>>) body.get("content");

                if (content != null) {
                    List<String> bankAccountIds = content.stream()
                        .map(acc -> {
                            Object bankAccId = acc.get("bankAccountId");
                            if (bankAccId != null && !bankAccId.toString().isBlank()) {
                                return bankAccId.toString();
                            }
                            // Fallback to accountId if bankAccountId is not set
                            Object accId = acc.get("accountId");
                            return accId != null ? accId.toString() : null;
                        })
                        .filter(id -> id != null && !id.isBlank())
                        .distinct()
                        .collect(Collectors.toList());

                    log.info("Resolved {} bank account IDs for group {}", bankAccountIds.size(), groupId);
                    return bankAccountIds;
                }
            }

            log.warn("No accounts found for group {}", groupId);
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("Failed to resolve bank account IDs for group {}: {}", groupId, e.getMessage());
            return Collections.emptyList();
        }
    }

    private String getPreview(String content) {
        if (content != null && content.length() > 50) {
            return content.substring(0, 50) + "...";
        }
        return content;
    }

    private String getLastMessagePreview(SendMessageRequest request) {
        if ("IMAGE".equals(request.getMessageType())) {
            return "📷 Hình ảnh";
        } else if ("FILE".equals(request.getMessageType())) {
            return "📎 " + (request.getFileName() != null ? request.getFileName() : "Tệp đính kèm");
        }
        String content = request.getContent();
        if (content != null && content.length() > 50) {
            return content.substring(0, 50) + "...";
        }
        return content;
    }

    @Override
    public Page<ChatMessageDto> getMessages(String chatRoomId, Pageable pageable) {
        Page<ChatMessage> messages = chatMessageRepository
                .findByChatRoomIdAndIsDeletedFalseOrderByCreatedAtDesc(chatRoomId, pageable);
        return messages.map(this::mapToDto);
    }

    @Override
    public List<ChatMessageDto> getRecentMessages(String chatRoomId, int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<ChatMessage> messages = chatMessageRepository.findRecentMessagesByChatRoomId(chatRoomId, pageable);
        return messages.stream()
                .map(this::mapToDto)
                .collect(Collectors.toList());
    }

    @Override
    public ChatMessageDto getMessageById(String messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatMessage", "id", messageId));
        return mapToDto(message);
    }

    @Override
    @Transactional
    public ChatMessageDto updateMessage(String messageId, String newContent) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatMessage", "id", messageId));
        
        message.setContent(newContent);
        message.setIsEdited(true);
        message = chatMessageRepository.save(message);
        
        return mapToDto(message);
    }

    @Override
    @Transactional
    public void deleteMessage(String messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("ChatMessage", "id", messageId));
        
        message.setIsDeleted(true);
        chatMessageRepository.save(message);
        log.info("Deleted (soft) message {}", messageId);
    }

    @Override
    @Transactional
    public void markMessagesAsRead(String chatRoomId, Long userId) {
        // Find all unread messages for this user in this room
        List<ChatMessage> unreadMessages = chatMessageRepository
                .findUnreadMessagesForUser(chatRoomId, userId);
        
        if (unreadMessages.isEmpty()) {
            log.debug("No unread messages for user {} in room {}", userId, chatRoomId);
            return;
        }
        
        // Mark all as read
        unreadMessages.forEach(message -> {
            message.getReadBy().add(userId);
        });
        
        chatMessageRepository.saveAll(unreadMessages);
        log.info("Marked {} messages as read for user {} in room {}", unreadMessages.size(), userId, chatRoomId);
    }

    @Override
    public long getUnreadCount(String chatRoomId, Long userId) {
        Long count = chatMessageRepository.countUnreadMessages(chatRoomId, userId);
        return count != null ? count : 0L;
    }

    @Override
    @Transactional
    public ChatRoomDto syncChatRoomWithGroup(Long groupId, String groupName, String avatarFileId, List<Long> memberIds) {
        log.info("Syncing chat room with group: {} with members: {}", groupId, memberIds);
        
        ChatRoom chatRoom = chatRoomRepository.findByGroupId(groupId)
                .orElse(null);

        if (chatRoom == null) {
            // Create new chat room
            chatRoom = ChatRoom.builder()
                    .groupId(groupId)
                    .name(groupName)
                    .avatarFileId(avatarFileId)
                    .memberIds(memberIds != null ? new ArrayList<>(memberIds) : new ArrayList<>())
                    .isActive(true)
                    .build();
        } else {
            // Update existing chat room
            chatRoom.setName(groupName);
            if (avatarFileId != null) {
                chatRoom.setAvatarFileId(avatarFileId);
            }
            // Merge member IDs instead of replacing
            if (memberIds != null && !memberIds.isEmpty()) {
                List<Long> existingMembers = chatRoom.getMemberIds();
                if (existingMembers == null) {
                    existingMembers = new ArrayList<>();
                }
                for (Long memberId : memberIds) {
                    if (!existingMembers.contains(memberId)) {
                        existingMembers.add(memberId);
                        log.info("Added member {} to chat room for group {}", memberId, groupId);
                    }
                }
                chatRoom.setMemberIds(existingMembers);
            }
        }

        chatRoom = chatRoomRepository.save(chatRoom);
        log.info("Chat room synced with ID: {}, members: {}", chatRoom.getId(), chatRoom.getMemberIds());
        
        return mapToDto(chatRoom);
    }

    @Override
    public List<Long> getRoomMemberIds(String roomId) {
        return (List<Long>) chatRoomRepository.findById(roomId)
                .map(room -> room.getMemberIds() != null ? room.getMemberIds() : new ArrayList<>())
                .orElse(new ArrayList<>());
    }

    @Override
    public Page<ChatMessageDto> searchMessages(String roomId, String keyword, Pageable pageable) {
        return chatMessageRepository.searchMessages(roomId, keyword, pageable).map(this::mapToDto);
    }

    @Override
    public Page<ChatMessageDto> getMediaMessages(String roomId, Pageable pageable) {
        return chatMessageRepository.findByChatRoomIdAndMessageTypeAndIsDeletedFalse(roomId, "IMAGE", pageable)
                .map(this::mapToDto);
    }

    @Override
    public Page<ChatMessageDto> getFileMessages(String roomId, Pageable pageable) {
        return chatMessageRepository.findByChatRoomIdAndMessageTypeAndIsDeletedFalse(roomId, "FILE", pageable)
                .map(this::mapToDto);
    }

    @Override
    public Page<ChatMessageDto> getLinkMessages(String roomId, Pageable pageable) {
        return chatMessageRepository.findLinkMessages(roomId, pageable).map(this::mapToDto);
    }

    // Mapping methods
    private ChatRoomDto mapToDto(ChatRoom chatRoom) {
        return ChatRoomDto.builder()
                .id(chatRoom.getId())
                .groupId(chatRoom.getGroupId())
                .name(chatRoom.getName())
                .avatarFileId(chatRoom.getAvatarFileId())
                .lastMessageSenderId(chatRoom.getLastMessageSenderId())
                .lastMessageContent(chatRoom.getLastMessageContent())
                .lastMessageTime(chatRoom.getLastMessageTime())
                .lastMessageType(chatRoom.getLastMessageType())
                .unreadCount(chatRoom.getUnreadCount())
                .isActive(chatRoom.getIsActive())
                .createdAt(chatRoom.getCreatedAt() != null ? chatRoom.getCreatedAt().toString() : null)
                .updatedAt(chatRoom.getUpdatedAt() != null ? chatRoom.getUpdatedAt().toString() : null)
                .build();
    }

    private ChatRoomDto mapToDtoWithUnreadCount(ChatRoom chatRoom, Long userId) {
        // Calculate unread count for this specific user
        Long count = chatMessageRepository.countUnreadMessages(chatRoom.getId(), userId);
        long unreadCount = count != null ? count : 0L;
        
        return ChatRoomDto.builder()
                .id(chatRoom.getId())
                .groupId(chatRoom.getGroupId())
                .name(chatRoom.getName())
                .avatarFileId(chatRoom.getAvatarFileId())
                .lastMessageSenderId(chatRoom.getLastMessageSenderId())
                .lastMessageContent(chatRoom.getLastMessageContent())
                .lastMessageTime(chatRoom.getLastMessageTime())
                .lastMessageType(chatRoom.getLastMessageType())
                .unreadCount(unreadCount)
                .isActive(chatRoom.getIsActive())
                .createdAt(chatRoom.getCreatedAt() != null ? chatRoom.getCreatedAt().toString() : null)
                .updatedAt(chatRoom.getUpdatedAt() != null ? chatRoom.getUpdatedAt().toString() : null)
                .build();
    }

    private ChatMessageDto mapToDto(ChatMessage message) {
        return ChatMessageDto.builder()
                .id(message.getId())
                .chatRoomId(message.getChatRoomId())
                .senderId(message.getSenderId())
                .senderName(message.getSenderName())
                .senderAvatar(message.getSenderAvatar())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .fileId(message.getFileId())
                .fileName(message.getFileName())
                .fileType(message.getFileType())
                .fileSize(message.getFileSize())
                .fileLiveUrl(message.getFileLiveUrl())
                .replyToMessageId(message.getReplyToMessageId())
                .replyToContent(message.getReplyToContent())
                .mentionsAI(message.getMentionsAI())
                .readBy(message.getReadBy())
                .isDeleted(message.getIsDeleted())
                .isEdited(message.getIsEdited())
                .createdAt(message.getCreatedAt() != null ? message.getCreatedAt().toString() : null)
                .updatedAt(message.getUpdatedAt() != null ? message.getUpdatedAt().toString() : null)
                .build();
    }
}
