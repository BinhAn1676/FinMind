package com.finance.notificationservice.service;

import com.finance.notificationservice.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NotificationService {
    
    /**
     * Create a new notification
     */
    Notification createNotification(Notification notification);
    
    /**
     * Get a notification by ID
     */
    Notification getNotification(String id);
    
    /**
     * Get all notifications for a user
     */
    List<Notification> getUserNotifications(Long userId);
    
    /**
     * Get paginated notifications for a user
     */
    Page<Notification> getUserNotifications(Long userId, Pageable pageable);
    
    /**
     * Get paginated notifications for a user with page and size
     */
    Page<Notification> getUserNotifications(Long userId, int page, int size);
    
    /**
     * Get unread notifications for a user
     */
    List<Notification> getUnreadNotifications(Long userId);
    
    /**
     * Get paginated unread notifications for a user
     */
    Page<Notification> getUnreadNotifications(Long userId, Pageable pageable);
    
    /**
     * Get paginated unread notifications for a user with page and size
     */
    Page<Notification> getUnreadNotifications(Long userId, int page, int size);
    
    /**
     * Get the count of unread notifications for a user
     */
    long getUnreadNotificationsCount(Long userId);
    
    /**
     * Get notifications of a specific type for a user
     */
    List<Notification> getNotificationsByType(Long userId, String type);
    
    /**
     * Get paginated notifications of a specific type for a user
     */
    Page<Notification> getNotificationsByType(Long userId, String type, Pageable pageable);
    
    /**
     * Get paginated notifications of a specific type for a user with page and size
     */
    Page<Notification> getNotificationsByType(Long userId, String type, int page, int size);
    
    /**
     * Mark a specific notification as read
     */
    void markAsRead(String notificationId);
    
    /**
     * Mark all notifications for a user as read
     */
    int markAllAsRead(Long userId);
    
    /**
     * Delete a notification
     */
    void deleteNotification(String id);
    
    /**
     * Delete all notifications for a user
     */
    void deleteAllUserNotifications(Long userId);
    
    /**
     * Delete old notifications (for cleanup tasks)
     */
    int deleteOldNotifications(int daysToRetain);

    /**
     * Get all notifications for a user with pagination and sorting
     */
    Page<Notification> getAllNotifications(Long userId, int page, int size);
}