package com.finance.notificationservice.controller;

import com.finance.notificationservice.entity.Notification;
import com.finance.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class NotificationWebSocketController {

    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Get unread notifications count via WebSocket
     */
    @MessageMapping("/notifications/count")
    public void getUnreadNotificationsCount(@Payload Map<String, String> payload) {
        Long userId = Long.valueOf(String.valueOf(payload.get("userId")));
        log.debug("WebSocket: Getting unread notifications count for user {}", userId);
        long count = notificationService.getUnreadNotificationsCount(userId);
        sendToUser(userId, "/queue/notifications-count", count);
    }

    /**
     * Get latest notifications via WebSocket with pagination
     */
    @MessageMapping("/notifications/latest")
    public void getLatestNotifications(@Payload Map<String, Object> payload) {
        Long userId = Long.valueOf(String.valueOf(payload.get("userId")));
        int page = payload.containsKey("page") ? Integer.parseInt(payload.get("page").toString()) : 0;
        int size = payload.containsKey("size") ? Integer.parseInt(payload.get("size").toString()) : 10;

        log.debug("WebSocket: Getting latest notifications for user {} (page: {}, size: {})", userId, page, size);
        Page<Notification> result = notificationService.getUnreadNotifications(userId, page, size);
        sendToUser(userId, "/queue/notifications-latest", result);
    }

    /**
     * Get all notifications via WebSocket with pagination
     */
    @MessageMapping("/notifications/all")
    public void getAllNotifications(@Payload Map<String, Object> payload) {
        Long userId = Long.valueOf(String.valueOf(payload.get("userId")));
        int page = payload.containsKey("page") ? Integer.parseInt(payload.get("page").toString()) : 0;
        int size = payload.containsKey("size") ? Integer.parseInt(payload.get("size").toString()) : 10;

        log.debug("WebSocket: Getting all notifications for user {} (page: {}, size: {})", userId, page, size);
        Page<Notification> result = notificationService.getAllNotifications(userId, page, size);
        sendToUser(userId, "/queue/notifications-all", result);
    }

    /**
     * Mark notification as read via WebSocket
     */
    @MessageMapping("/notifications/mark-read")
    public void markNotificationAsRead(@Payload Map<String, Object> payload) {
        Long userId = Long.valueOf(String.valueOf(payload.get("userId")));
        String notificationId = payload.get("notificationId").toString();

        log.debug("WebSocket: Marking notification {} as read for user {}", notificationId, userId);

        Notification notification = notificationService.getNotification(notificationId);
        if (notification.getUserId().equals(userId)) {
            notificationService.markAsRead(notificationId);
            long count = notificationService.getUnreadNotificationsCount(userId);
            sendToUser(userId, "/queue/notifications-count", count);
        } else {
            log.warn("User {} attempted to mark notification {} owned by {} as read",
                    userId, notificationId, notification.getUserId());
        }
    }

    /**
     * Mark all notifications as read via WebSocket
     */
    @MessageMapping("/notifications/mark-all-read")
    public void markAllNotificationsAsRead(@Payload Map<String, String> payload) {
        Long userId = Long.valueOf(String.valueOf(payload.get("userId")));
        log.debug("WebSocket: Marking all notifications as read for user {}", userId);

        notificationService.markAllAsRead(userId);
        sendToUser(userId, "/queue/notifications-count", 0L);
    }

    private void sendToUser(Long userId, String destination, Object payload) {
        messagingTemplate.convertAndSend("/user/" + userId + destination, payload);
    }
} 