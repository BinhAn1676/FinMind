package com.finance.userservice.dto.group;

import com.finance.userservice.constant.GroupActivityType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GroupActivityDto {
    private Long id;
    private Long groupId;
    private Long actorUserId;
    private String actorName;
    private GroupActivityType type;
    private String message;
    private String metadata;
    private LocalDateTime createdAt;
}


