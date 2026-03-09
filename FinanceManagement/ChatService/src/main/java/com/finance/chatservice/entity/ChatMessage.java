package com.finance.chatservice.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

@Document(collection = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndex(def = "{'chatRoomId': 1, 'createdAt': -1}")
public class ChatMessage extends BaseEntity {

    @Id
    private String id;

    @Indexed
    private String chatRoomId;

    @Indexed
    private Long senderId;

    private String senderName;

    private String senderAvatar;

    private String content;

    // TEXT, IMAGE, FILE, SYSTEM, STICKER
    @Builder.Default
    private String messageType = "TEXT";

    // For file/image messages
    private String fileId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String fileLiveUrl;

    // For mentions/replies
    private String replyToMessageId;

    private String replyToContent;

    // AI mention flag
    @Builder.Default
    private Boolean mentionsAI = false;

    // Read receipts - list of user IDs who have read this message
    @Builder.Default
    private List<Long> readBy = new ArrayList<>();

    @Builder.Default
    private Boolean isDeleted = false;

    @Builder.Default
    private Boolean isEdited = false;
}
