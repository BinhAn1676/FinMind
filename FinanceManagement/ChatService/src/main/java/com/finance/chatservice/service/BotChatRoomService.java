package com.finance.chatservice.service;

import com.finance.chatservice.dto.ChatRoomDto;
import com.finance.chatservice.entity.ChatRoom;
import com.finance.chatservice.repository.ChatRoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for managing 1-on-1 chat rooms with AI Bot (FinBot).
 *
 * Automatically creates a dedicated chat room between any user and FinBot.
 * This chat room always appears in the user's chat list.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BotChatRoomService {

    private static final Long FINBOT_USER_ID = 999999L;
    private static final String FINBOT_ROOM_PREFIX = "bot_";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ChatRoomRepository chatRoomRepository;

    /**
     * Get or create a 1-on-1 chat room between user and FinBot.
     *
     * @param userId User's ID
     * @return Chat room DTO
     */
    @Transactional
    public ChatRoomDto getOrCreateBotChatRoom(Long userId) {
        log.info("Getting or creating bot chat room for user {}", userId);

        // Check if bot chat room already exists for this user
        String roomId = FINBOT_ROOM_PREFIX + userId;
        ChatRoom existingRoom = chatRoomRepository.findById(roomId).orElse(null);

        if (existingRoom != null) {
            log.debug("Bot chat room already exists: {}", roomId);
            return mapToDto(existingRoom);
        }

        // Create new 1-on-1 chat room with FinBot
        ChatRoom botChatRoom = ChatRoom.builder()
                .id(roomId)  // Fixed ID format: bot_{userId}
                .groupId(null)  // Null for 1-on-1 chats
                .name("FinBot - Trợ lý Tài chính AI")
                .avatarFileId("/assets/avatar/finbot.png")
                .memberIds(List.of(userId, FINBOT_USER_ID))
                .isActive(true)
                .lastMessageContent("Xin chào! Tôi là FinBot, trợ lý tài chính AI của bạn. Hỏi tôi bất cứ điều gì về chi tiêu và tài chính của bạn!")
                .lastMessageTime(LocalDateTime.now().format(DATE_FORMATTER))
                .lastMessageSenderId(FINBOT_USER_ID)
                .lastMessageType("TEXT")
                .build();

        botChatRoom = chatRoomRepository.save(botChatRoom);
        log.info("Created new bot chat room: {} for user {}", roomId, userId);

        return mapToDto(botChatRoom);
    }

    /**
     * Check if a chat room is a bot chat room.
     */
    public boolean isBotChatRoom(String roomId) {
        return roomId != null && roomId.startsWith(FINBOT_ROOM_PREFIX);
    }

    /**
     * Get FinBot user ID.
     */
    public Long getFinBotUserId() {
        return FINBOT_USER_ID;
    }

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
}
