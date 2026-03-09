package com.finance.notificationservice.service.impl.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.notificationservice.common.config.KafkaConfig;
import com.finance.notificationservice.model.kafka.model.UserEvent;
import com.finance.notificationservice.service.NotifyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Service that listens to Kafka topics and processes events
 */
@Service
@Slf4j
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class KafkaListenerService {
    private final ObjectMapper objectMapper;
    private final NotifyService notifyService;

    /**
     * Listen to user events
     */
    @KafkaListener(topics = KafkaConfig.USER_EVENTS_TOPIC, groupId = "${spring.kafka.consumer.group-id}")
    public void processUserEvents(@Payload String payload, 
                                 @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                 @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key) {
        try {
            log.info("Received user event: topic={}, key={}", topic, key);
            log.debug("Raw payload: {}", payload);
            
            // Manually deserialize the JSON string to UserEvent
            UserEvent event = objectMapper.readValue(payload, UserEvent.class);
            
            log.info("Processed user event: eventType={}", event.getEventType());
            
            String eventType = event.getEventType();
            if (eventType == null) {
                log.warn("UserEvent has null eventType");
                return;
            }
            // Still handle specific events for email sending
            switch (eventType) {
                case "USER_CREATED":
                    handleUserCreated(event);
                    break;
                case "USER_UPDATED":
                    handleUserUpdated(event);
                    break;
                case "PASSWORD_CHANGED":
                    handlePasswordChanged(event);
                    break;
                default:
                    log.info("Unhandled user event type: {}", eventType);
            }
        } catch (JsonProcessingException e) {
            log.error("Error deserializing user event: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error processing user event: {}", e.getMessage(), e);
        }
    }

    private void handleUserCreated(UserEvent event) {
        log.info("User created: email={}, fullName={}", event.getEmail(), event.getFullName());
        
        // Send email
        notifyService.sendEmail(
            event.getEmail(),
            "Welcome to Finance Management",
            "Welcome " + event.getFullName() + "! Your account has been created successfully."
        );
    }
    
    private void handleUserUpdated(UserEvent event) {
        log.info("User updated: email={}", event.getEmail());
        
        // Send email notification
        notifyService.sendEmail(
            event.getEmail(),
            "Profile Updated",
            "Your profile information has been updated successfully."
        );
    }
    
    private void handlePasswordChanged(UserEvent event) {
        log.info("Password changed: email={}", event.getEmail());
        
        // Send email notification
        notifyService.sendEmail(
            event.getEmail(),
            "Password Changed",
            "Your password has been changed successfully. If you did not make this change, please contact support immediately."
        );

    }
}