package com.finance.userservice.dto.group;

import lombok.Data;

@Data
public class GroupUpdateRequest {
    private String name;
    private String description;
    private String avatarFileId;
}

