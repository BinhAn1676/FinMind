package com.finance.userservice.dto.group;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class GroupCreateRequest {
    @NotBlank
    private String name;
    private String description;
    private String avatarFileId;
    private List<Long> invitedUserIds;
}


