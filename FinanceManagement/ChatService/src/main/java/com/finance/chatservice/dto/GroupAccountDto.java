package com.finance.chatservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO for group account info from UserService.
 * Only contains fields needed for AI group context resolution.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupAccountDto {
    private Long id;
    private Long accountId;
    private String bankAccountId;
    private Long ownerUserId;
    private String label;
}
