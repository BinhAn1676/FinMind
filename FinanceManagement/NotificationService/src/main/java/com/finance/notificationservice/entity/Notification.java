package com.finance.notificationservice.entity;

import com.finance.notificationservice.constants.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.HashMap;
import java.util.Map;

@Document(collection = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification extends BaseEntity {

    @Id
    private String id;

    @Field("user_id")
    private Long userId;

    @Field("type")
    private NotificationType type;

    @Field("title")
    private String title;

    @Field("message")
    private String message;

    @Field("read")
    private boolean read;

    @Field("source")
    private String source;

    @Field("metadata")
    private Map<String, String> metadata = new HashMap<>();
} 