package com.finance.chatservice.repository;

import com.finance.chatservice.entity.ChatRoom;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends MongoRepository<ChatRoom, String> {

    Optional<ChatRoom> findByGroupId(Long groupId);

    @Query("{ 'memberIds': ?0, 'isActive': true }")
    Page<ChatRoom> findByMemberIdAndActive(Long memberId, Pageable pageable);

    @Query("{ 'memberIds': ?0, 'isActive': true }")
    List<ChatRoom> findAllByMemberIdAndActive(Long memberId);

    @Query(value = "{ 'memberIds': ?0, 'isActive': true }", 
           sort = "{ 'lastMessageTime': -1 }")
    List<ChatRoom> findRecentChatRoomsByMemberId(Long memberId);

    boolean existsByGroupId(Long groupId);
}
