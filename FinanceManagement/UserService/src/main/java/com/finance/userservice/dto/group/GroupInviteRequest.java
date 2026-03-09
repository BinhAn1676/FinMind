package com.finance.userservice.dto.group;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class GroupInviteRequest {
    @NotEmpty
    private List<Long> inviteeUserIds;
}


