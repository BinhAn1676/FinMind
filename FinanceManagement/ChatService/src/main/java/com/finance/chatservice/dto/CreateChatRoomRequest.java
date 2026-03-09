package com.finance.chatservice.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateChatRoomRequest {

    @NotNull(message = "Group ID is required")
    private Long groupId;

    private String name;

    private String avatarFileId;

    private List<Long> memberIds;
}
