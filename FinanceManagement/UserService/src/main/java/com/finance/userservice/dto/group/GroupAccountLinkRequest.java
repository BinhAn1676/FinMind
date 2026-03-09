package com.finance.userservice.dto.group;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GroupAccountLinkRequest {
    @NotNull
    private Long accountId;
}













