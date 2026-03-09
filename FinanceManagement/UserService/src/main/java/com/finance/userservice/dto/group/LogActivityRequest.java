package com.finance.userservice.dto.group;

import com.finance.userservice.constant.GroupActivityType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

@Data
public class LogActivityRequest {
    @NotNull
    private Long groupId;
    
    @NotNull
    private Long actorUserId;
    
    private String actorName;
    
    @NotNull
    private GroupActivityType type;
    
    private String message;
    
    private Map<String, Object> metadata;
}


