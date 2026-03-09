package com.finance.userservice.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.userservice.common.config.KafkaConfig;
import com.finance.userservice.entity.User;
import com.finance.userservice.event.UserEvent;
import com.finance.userservice.event.UserNotificationEvent;
import com.finance.userservice.service.KafkaProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerServiceImpl implements KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Sends a user event to the user-events topic
     */
    @Async
    @Override
    public void sendUserEvent(UserEvent event) {
        try {
            String key = event.getUserId() != null ? event.getUserId() : "unknown";
            
            log.info("Sending user event to Kafka: topic={}, key={}, eventType={}", 
                    KafkaConfig.USER_EVENTS_TOPIC, key, event.getEventType());
            log.debug("Event payload: {}", event);
            
            sendToTopic(KafkaConfig.USER_EVENTS_TOPIC, key, event);
        } catch (Exception e) {
            log.error("Failed to send user event to Kafka: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send event", e);
        }
    }
    
    /**
     * Sends a notification event to the user-notification topic
     */
    @Async
    @Override
    public void sendNotificationEvent(UserNotificationEvent event) {
        try {
            String key = event.getUserId() != null ? event.getUserId().toString() : "unknown";
            
            log.info("Sending notification event to Kafka: topic={}, key={}, eventType={}", 
                    KafkaConfig.USER_NOTIFICATION_TOPIC, key, event.getEventType());
            log.debug("Event payload: {}", event);
            
            sendToTopic(KafkaConfig.USER_NOTIFICATION_TOPIC, key, event);
        } catch (Exception e) {
            log.error("Failed to send notification event to Kafka: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send event", e);
        }
    }
    

    /**
     * Send a message to a Kafka topic
     */
    private void sendToTopic(String topic, String key, Object payload) {
        try {
            log.info("Attempting to send message to Kafka topic: {}, key: {}", topic, key);
            
            // Send synchronously and wait for result
            var future = kafkaTemplate.send(topic, key, payload);
            var result = future.get(); // This will block until send completes or fails
            
            log.info("Successfully sent message to topic={}, key={}, partition={}, offset={}", 
                    topic, key, result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
                    
        } catch (Exception e) {
            log.error("Failed to send message to topic={}, key={}: {}", topic, key, e.getMessage(), e);
            throw new RuntimeException("Failed to send message to Kafka", e);
        }
    }

}