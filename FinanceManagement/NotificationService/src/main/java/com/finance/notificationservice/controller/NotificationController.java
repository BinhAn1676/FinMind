package com.finance.notificationservice.controller;

import com.finance.notificationservice.entity.Notification;
import com.finance.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * Get all notifications for the user with pagination
     */
    @GetMapping
    public ResponseEntity<Page<Notification>> getUserNotifications(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, page, size));
    }

    /**
     * Get paginated notifications for the user (kept for compatibility)
     */
    @GetMapping("/paged")
    public ResponseEntity<Page<Notification>> getPagedUserNotifications(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(notificationService.getUserNotifications(userId, page, size));
    }

    /**
     * Get unread notifications for the user with pagination
     */
    @GetMapping("/unread")
    public ResponseEntity<Page<Notification>> getUnreadNotifications(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(notificationService.getUnreadNotifications(userId, page, size));
    }

    /**
     * Get count of unread notifications for the user
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Long>> getUnreadNotificationsCount(@RequestParam Long userId) {
        long count = notificationService.getUnreadNotificationsCount(userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    /**
     * Get notifications by type for the user with pagination
     */
    @GetMapping("/type/{type}")
    public ResponseEntity<Page<Notification>> getNotificationsByType(
            @RequestParam Long userId,
            @PathVariable String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(notificationService.getNotificationsByType(userId, type, page, size));
    }

    /**
     * Mark a notification as read
     */
    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markNotificationAsRead(
            @RequestParam Long userId,
            @PathVariable String id) {
        // Security check to ensure user can only mark their own notifications
        Notification notification = notificationService.getNotification(id);
        
        if (!notification.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    /**
     * Mark all notifications as read for the user
     */
    @PutMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllNotificationsAsRead(@RequestParam Long userId) {
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("markedCount", count));
    }

    /**
     * Delete a notification
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @RequestParam Long userId,
            @PathVariable String id) {
        // Security check to ensure user can only delete their own notifications
        Notification notification = notificationService.getNotification(id);
        
        if (!notification.getUserId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        
        notificationService.deleteNotification(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Delete all notifications for the user
     */
    @DeleteMapping
    public ResponseEntity<Void> deleteAllUserNotifications(@RequestParam Long userId) {
        notificationService.deleteAllUserNotifications(userId);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Get all notifications for the user with pagination and sorting
     */
    @GetMapping("/all")
    public ResponseEntity<Page<Notification>> getAllNotifications(
            @RequestParam Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(notificationService.getAllNotifications(userId, page, size));
    }
} 