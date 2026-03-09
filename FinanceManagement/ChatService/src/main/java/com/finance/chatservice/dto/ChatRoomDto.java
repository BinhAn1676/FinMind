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
public class ChatRoomDto {

    private String id;

    private Long groupId;

    private String name;

    private String avatarFileId;

    private String avatarLiveUrl;

    private List<ChatMemberDto> members;

    private Long lastMessageSenderId;

    private String lastMessageSenderName;

    private String lastMessageContent;

    private String lastMessageTime;

    private String lastMessageType;

    private Long unreadCount;

    private Boolean isActive;

    private String createdAt;

    private String updatedAt;
}
