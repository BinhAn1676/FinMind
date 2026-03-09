package com.finance.userservice.dto.group;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class GroupAccountDto {
    private Long id;
    private Long accountId;
    private String bankAccountId;
    private Long ownerUserId;
    private String label;
    private String bankBrandName;
    private String accountNumber;
    private String accumulated;
    private String currency;
    private String bankCode;
    private Instant linkedAt;
}





