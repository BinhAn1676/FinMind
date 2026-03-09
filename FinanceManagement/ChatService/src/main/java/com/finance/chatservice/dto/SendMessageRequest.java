package com.finance.chatservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMessageRequest {

    @NotBlank(message = "Chat room ID is required")
    private String chatRoomId;

    @NotNull(message = "Sender ID is required")
    private Long senderId;

    private String senderName;

    private String senderAvatar;

    private String content;

    @Builder.Default
    private String messageType = "TEXT"; // TEXT, IMAGE, FILE

    // For file/image uploads
    private String fileId;

    private String fileName;

    private String fileType;

    private Long fileSize;

    // For replies
    private String replyToMessageId;

    private String replyToContent;

    // AI mention
    @Builder.Default
    private Boolean mentionsAI = false;

    // JWT token for service-to-service authentication (AI calls)
    private String jwtToken;
}
