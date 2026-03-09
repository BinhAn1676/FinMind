package com.finance.financeservice.common.config;

import com.finance.financeservice.event.UserNotificationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

/**
 * Kafka configuration for FinanceService
 * Produces notification events to the shared user-notification topic
 * Phase 5: Web Notifications
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    // Topic name - matches NotificationService topic
    public static final String USER_NOTIFICATION_TOPIC = "user-notification";

    /**
     * KafkaTemplate for sending UserNotificationEvent to notification service
     */
    @Bean
    public KafkaTemplate<String, UserNotificationEvent> notificationKafkaTemplate(
            ProducerFactory<String, UserNotificationEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    @Bean
    public StringJsonMessageConverter jsonMessageConverter() {
        return new StringJsonMessageConverter();
    }
}