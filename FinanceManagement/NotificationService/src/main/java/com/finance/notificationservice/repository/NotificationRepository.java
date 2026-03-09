package com.finance.notificationservice.repository;

import com.finance.notificationservice.constants.NotificationType;
import com.finance.notificationservice.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {
    
    /**
     * Find notifications for a specific user
     */
    List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);
    
    /**
     * Find paginated notifications for a specific user
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    /**
     * Find unread notifications for a specific user
     */
    List<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId);
    
    /**
     * Find paginated unread notifications for a specific user
     */
    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    /**
     * Count unread notifications for a specific user
     */
    long countByUserIdAndReadFalse(Long userId);
    
    /**
     * Find notifications by type for a specific user
     */
    List<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, NotificationType type);
    
    /**
     * Find paginated notifications by type for a specific user
     */
    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, NotificationType type, Pageable pageable);
    
    /**
     * Delete old notifications (for cleanup tasks)
     */
    void deleteByCreatedAtBefore(LocalDateTime before);
} 