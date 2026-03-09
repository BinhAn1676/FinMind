package com.finance.notificationservice.service.impl;

import com.finance.notificationservice.constants.NotificationType;
import com.finance.notificationservice.entity.Notification;
import com.finance.notificationservice.exception.ResourceNotFoundException;
import com.finance.notificationservice.repository.NotificationRepository;
import com.finance.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Override
    @Transactional
    public Notification createNotification(Notification notification) {
        log.info("Creating notification for user: {}", notification.getUserId());
        Notification savedNotification = notificationRepository.save(notification);
        
        // Send WebSocket notification to the user
        String destination = "/user/" + notification.getUserId() + "/queue/notifications";
        messagingTemplate.convertAndSend(destination, savedNotification);
        
        // Update unread count
        updateUnreadCount(notification.getUserId());
        
        return savedNotification;
    }

    @Override
    public Notification getNotification(String id) {
        return notificationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Id","Id", id));
    }

    @Override
    public List<Notification> getUserNotifications(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Override
    public Page<Notification> getUserNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
    
    @Override
    public Page<Notification> getUserNotifications(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public List<Notification> getUnreadNotifications(Long userId) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
    }
    
    @Override
    public Page<Notification> getUnreadNotifications(Long userId, Pageable pageable) {
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable);
    }
    
    @Override
    public Page<Notification> getUnreadNotifications(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId, pageable);
    }

    @Override
    public long getUnreadNotificationsCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFalse(userId);
    }

    @Override
    public List<Notification> getNotificationsByType(Long userId, String type) {
        NotificationType notificationType = NotificationType.valueOf(type);
        return notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, notificationType);
    }
    
    @Override
    public Page<Notification> getNotificationsByType(Long userId, String type, Pageable pageable) {
        NotificationType notificationType = NotificationType.valueOf(type);
        return notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, notificationType, pageable);
    }
    
    @Override
    public Page<Notification> getNotificationsByType(Long userId, String type, int page, int size) {
        NotificationType notificationType = NotificationType.valueOf(type);
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(userId, notificationType, pageable);
    }

    @Override
    @Transactional
    public void markAsRead(String notificationId) {
        Notification notification = getNotification(notificationId);
        notification.setRead(true);
        notificationRepository.save(notification);
        
        // Update unread count
        updateUnreadCount(notification.getUserId());
    }

    @Override
    @Transactional
    public int markAllAsRead(Long userId) {
        List<Notification> unreadNotifications = notificationRepository.findByUserIdAndReadFalseOrderByCreatedAtDesc(userId);
        int count = unreadNotifications.size();
        
        unreadNotifications.forEach(notification -> {
            notification.setRead(true);
            notificationRepository.save(notification);
        });
        
        // Update unread count
        updateUnreadCount(userId);
        
        return count;
    }

    @Override
    @Transactional
    public void deleteNotification(String id) {
        Notification notification = getNotification(id);
        Long userId = notification.getUserId();
        
        notificationRepository.deleteById(id);
        
        // Update unread count if the deleted notification was unread
        if (!notification.isRead()) {
            updateUnreadCount(userId);
        }
    }

    @Override
    @Transactional
    public void deleteAllUserNotifications(Long userId) {
        List<Notification> notifications = getUserNotifications(userId);
        notificationRepository.deleteAll(notifications);
        
        // Update unread count
        updateUnreadCount(userId);
    }

    @Override
    @Transactional
    public int deleteOldNotifications(int daysToRetain) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToRetain);
        List<Notification> oldNotifications = notificationRepository.findAll().stream()
                .filter(n -> n.getCreatedAt() != null && n.getCreatedAt().isBefore(cutoffDate))
                .toList();
        int count = oldNotifications.size();
        notificationRepository.deleteAll(oldNotifications);
        return count;
    }

    @Override
    public Page<Notification> getAllNotifications(Long userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    /**
     * Helper method to update the unread count via WebSocket
     */
    private void updateUnreadCount(Long userId) {
        long unreadCount = notificationRepository.countByUserIdAndReadFalse(userId);
        String countDestination = "/user/" + userId + "/queue/notifications-count";
        messagingTemplate.convertAndSend(countDestination, unreadCount);
    }
} 