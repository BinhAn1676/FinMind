package com.finance.userservice.dto.user;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserContactDto {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private String phone;
}


