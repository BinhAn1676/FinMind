package com.finance.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDto {

    private String id;

    private String chatRoomId;

    private Long senderId;

    private String senderName;

    private String senderAvatar;

    private String senderAvatarLiveUrl;

    private String content;

    private String messageType; // TEXT, IMAGE, FILE, SYSTEM

    // File/Image info
    private String fileId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    private String fileLiveUrl;

    // Reply info
    private String replyToMessageId;

    private String replyToContent;

    private Boolean mentionsAI;

    private List<Long> readBy;

    private Boolean isDeleted;

    private Boolean isEdited;

    private String createdAt;

    private String updatedAt;
}
