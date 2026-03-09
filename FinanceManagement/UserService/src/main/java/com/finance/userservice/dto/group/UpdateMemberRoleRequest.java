package com.finance.userservice.dto.group;

import com.finance.userservice.constant.GroupRole;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateMemberRoleRequest {
    @NotNull(message = "Role is required")
    private GroupRole role;
}

