package com.finance.notificationservice.model.kafka.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationEvent {
    private String eventId;
    private String eventType;
    private Long userId;
    private String email;
    private String phone;
    private String username;
    private String fullName;
    private String message;
    private String title;
    private String source;


    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    //private Object additionalData;
    private Map<String, String> additionalData;
}
