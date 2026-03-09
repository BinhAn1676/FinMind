package com.finance.chatservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "chat_rooms")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom extends BaseEntity {

    @Id
    private String id;

    @Indexed
    private Long groupId; // Link to UserService group

    private String name;

    private String avatarFileId;

    @Builder.Default
    private List<Long> memberIds = new ArrayList<>();

    private Long lastMessageSenderId;

    private String lastMessageContent;

    private String lastMessageTime;

    private String lastMessageType;

    @Builder.Default
    private Long unreadCount = 0L;

    @Builder.Default
    private Boolean isActive = true;
}
