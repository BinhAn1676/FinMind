package com.finance.chatservice.service;

import com.finance.chatservice.dto.*;
import com.finance.chatservice.entity.ChatMessage;
import com.finance.chatservice.entity.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ChatService {

    // Chat Room operations
    ChatRoomDto createChatRoom(CreateChatRoomRequest request);

    ChatRoomDto getChatRoomById(String roomId);

    ChatRoomDto getChatRoomByGroupId(Long groupId);

    List<ChatRoomDto> getUserChatRooms(Long userId);

    Page<ChatRoomDto> getUserChatRoomsPaged(Long userId, Pageable pageable);

    ChatRoomDto updateChatRoom(String roomId, CreateChatRoomRequest request);

    void addMemberToRoom(String roomId, Long userId);

    void removeMemberFromRoom(String roomId, Long userId);

    void deleteChatRoom(String roomId);

    // Message operations
    ChatMessageDto sendMessage(SendMessageRequest request);

    Page<ChatMessageDto> getMessages(String chatRoomId, Pageable pageable);

    List<ChatMessageDto> getRecentMessages(String chatRoomId, int limit);

    ChatMessageDto getMessageById(String messageId);

    ChatMessageDto updateMessage(String messageId, String newContent);

    void deleteMessage(String messageId);

    void markMessagesAsRead(String chatRoomId, Long userId);

    long getUnreadCount(String chatRoomId, Long userId);

    // Sync with UserService groups
    ChatRoomDto syncChatRoomWithGroup(Long groupId, String groupName, String avatarFileId, List<Long> memberIds);
    
    // Get room member IDs for notifications
    List<Long> getRoomMemberIds(String roomId);

    // Sidebar panel queries
    Page<ChatMessageDto> searchMessages(String roomId, String keyword, Pageable pageable);
    Page<ChatMessageDto> getMediaMessages(String roomId, Pageable pageable);
    Page<ChatMessageDto> getFileMessages(String roomId, Pageable pageable);
    Page<ChatMessageDto> getLinkMessages(String roomId, Pageable pageable);
}
