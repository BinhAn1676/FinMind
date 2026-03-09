package com.finance.chatservice.repository;

import com.finance.chatservice.entity.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {
    
    Page<ChatMessage> findByChatRoomIdAndIsDeletedFalseOrderByCreatedAtDesc(
            String chatRoomId, Pageable pageable);

    @Query(value = "{ 'chatRoomId': ?0, 'isDeleted': false }", 
           sort = "{ 'createdAt': -1 }")
    List<ChatMessage> findRecentMessagesByChatRoomId(String chatRoomId, Pageable pageable);

    long countByChatRoomIdAndIsDeletedFalse(String chatRoomId);

    @Query(value = "{ 'chatRoomId': ?0, 'readBy': { $ne: ?1 }, 'isDeleted': false }", count = true)
    Long countUnreadMessages(String chatRoomId, Long userId);

    @Query("{ 'chatRoomId': ?0, 'readBy': { $ne: ?1 }, 'isDeleted': false }")
    List<ChatMessage> findUnreadMessagesForUser(String chatRoomId, Long userId);

    @Query("{ 'chatRoomId': ?0, 'senderId': ?1, 'isDeleted': false }")
    Page<ChatMessage> findBySenderInRoom(String chatRoomId, Long senderId, Pageable pageable);

    void deleteByChatRoomId(String chatRoomId);

    @Query("{ 'chatRoomId': ?0, 'content': { $regex: ?1, $options: 'i' }, 'isDeleted': false }")
    Page<ChatMessage> searchMessages(String chatRoomId, String keyword, Pageable pageable);

    Page<ChatMessage> findByChatRoomIdAndMessageTypeAndIsDeletedFalse(
            String chatRoomId, String messageType, Pageable pageable);

    @Query("{ 'chatRoomId': ?0, 'messageType': 'TEXT', 'content': { $regex: 'https?://', $options: 'i' }, 'isDeleted': false }")
    Page<ChatMessage> findLinkMessages(String chatRoomId, Pageable pageable);
}
