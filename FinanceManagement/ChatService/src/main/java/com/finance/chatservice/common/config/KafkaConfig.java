package com.finance.chatservice.common.config;


import com.finance.chatservice.model.event.model.ChatEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

@Configuration
@EnableKafka
public class KafkaConfig {

    // Define topic names as constants
    public static final String CHAT_EVENTS_TOPIC = "chat-events"; //for general user events
    public static final String CHAT_NOTIFICATION_TOPIC = "chat-notification"; // for notification bell socket
    
    /**
     * Creates the user-events topic
     * This topic will be used to publish all user-related events
     */
    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(CHAT_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
    
    /**
     * Creates the user-notifications topic
     * This topic will be used specifically for notification-worthy events
     */
    @Bean
    public NewTopic userNotificationTopic() {
        return TopicBuilder.name(CHAT_NOTIFICATION_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // KafkaTemplate with UserEvent type for values
    @Bean
    public KafkaTemplate<String, ChatEvent> kafkaTemplate(ProducerFactory<String, ChatEvent> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
    
    @Bean
    public StringJsonMessageConverter jsonMessageConverter() {
        return new StringJsonMessageConverter();
    }
} 