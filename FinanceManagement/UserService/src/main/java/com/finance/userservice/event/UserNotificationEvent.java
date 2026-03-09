package com.finance.userservice.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationEvent {
    private String eventId;
    private String eventType;
    private Long userId;
    private String email;
    private String fullName;
    private String phone;
    private String username;
    private String source;
    private String title;
    private String message;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    private LocalDateTime timestamp;

    private Object additionalData;

    public static final String GROUP_INVITATION = "GROUP_INVITATION";
    public static final String CHAT_ROOM_INVITATION = "CHAT_ROOM_INVITATION";
    public static final String GROUP_UPDATED = "GROUP_UPDATED";
    public static final String GROUP_DELETED = "GROUP_DELETED";
    public static final String MEMBER_REMOVED = "MEMBER_REMOVED";
    public static final String MEMBER_LEFT = "MEMBER_LEFT";
    public static final String MEMBER_ROLE_CHANGED = "MEMBER_ROLE_CHANGED";
    public static final String GROUP_ACCOUNT_LINKED = "GROUP_ACCOUNT_LINKED";
    public static final String GROUP_ACCOUNT_UNLINKED = "GROUP_ACCOUNT_UNLINKED";
}
