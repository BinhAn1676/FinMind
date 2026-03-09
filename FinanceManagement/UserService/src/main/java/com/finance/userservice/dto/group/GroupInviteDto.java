package com.finance.userservice.dto.group;

import com.finance.userservice.constant.GroupInviteStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class GroupInviteDto {
    private Long id;
    private Long groupId;
    private Long inviterUserId;
    private Long inviteeUserId;
    private String inviteeFullName;
    private String inviteeEmail;
    private String inviteePhone;
    private GroupInviteStatus status;
    private Instant createdAt;
    private Instant updatedAt;
}


