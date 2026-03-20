package com.finance.notificationservice.service.impl.kafka;

import com.finance.notificationservice.common.config.KafkaConfig;
import com.finance.notificationservice.constants.NotificationType;
import com.finance.notificationservice.entity.Notification;
import com.finance.notificationservice.model.kafka.model.UserNotificationEvent;
import com.finance.notificationservice.repository.NotificationRepository;
import com.finance.notificationservice.service.NotifyService;
import com.finance.notificationservice.service.impl.EmailTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationKafkaConsumer {

    private final NotificationRepository notificationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotifyService notifyService;
    private final EmailTemplateService emailTemplateService;

    @KafkaListener(topics = KafkaConfig.USER_NOTIFICATION_TOPIC, groupId = "notification-group")
    public void consumeNotification(UserNotificationEvent event) {
        log.info("Received notification event: {}", event);

        try {
            Map<String, String> metadata = new HashMap<>();
            if (event.getAdditionalData() != null) {
                metadata.putAll(event.getAdditionalData());
            }
            Notification notification = Notification.builder()
                    .userId(event.getUserId())
                    .type(NotificationType.valueOf(event.getEventType()))
                    .title(event.getTitle())
                    .message(event.getMessage())
                    .read(false)
                    .source(event.getSource())
                    .metadata(metadata)
                    .build();

            notification = notificationRepository.save(notification);

            // Push via WebSocket
            String destination = "/user/" + event.getUserId() + "/queue/notifications";
            messagingTemplate.convertAndSend(destination, notification);

            String countDestination = "/user/" + event.getUserId() + "/queue/notifications-count";
            long unreadCount = notificationRepository.countByUserIdAndReadFalse(event.getUserId());
            messagingTemplate.convertAndSend(countDestination, unreadCount);

            log.info("Notification processed and sent to user {}", event.getUserId());

            // Send email for BILL_REMINDER events
            if ("BILL_REMINDER".equals(event.getEventType()) && event.getEmail() != null && !event.getEmail().isBlank()) {
                String htmlBody = emailTemplateService.buildBillReminderEmail(event.getFullName(), event.getMessage());
                notifyService.sendEmail(event.getEmail(), "⏰ " + event.getTitle(), htmlBody);
            }

        } catch (Exception e) {
            log.error("Error processing notification event", e);
        }
    }
}
