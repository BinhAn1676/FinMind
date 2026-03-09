package com.finance.userservice.service;

import com.finance.userservice.event.UserEvent;
import com.finance.userservice.event.UserNotificationEvent;
import org.springframework.scheduling.annotation.Async;

public interface KafkaProducerService {
    @Async
    void sendUserEvent(UserEvent event);

    @Async
    void sendNotificationEvent(UserNotificationEvent event);
}
