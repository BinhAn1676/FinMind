package com.finance.userservice.dto.group;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class GroupDto {
    private Long id;
    private String name;
    private String description;
    private String avatarFileId;
    private Long ownerUserId;
    private Integer memberCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MemberDto> members;

    @Data
    @Builder
    public static class MemberDto {
        private Long userId;
        private String fullName;
        private String email;
        private String phone;
        private String avatar;
        private String role;
        private Instant joinedAt;
    }
}


