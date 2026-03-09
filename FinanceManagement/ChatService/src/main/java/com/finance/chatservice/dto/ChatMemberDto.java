package com.finance.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMemberDto {

    private Long userId;

    private String fullName;

    private String email;

    private String avatar;

    private String avatarLiveUrl;

    private String role; // OWNER, ADMIN, MEMBER

    private Boolean isOnline;

    private String lastSeen;
}
