package com.finance.userservice.dto.user;

import com.finance.userservice.constant.FileCode;
import lombok.Data;

@Data
public class UserFileDto {
    private String id;
    private String fileName;
    private String fileType;
    private String url;
    private FileCode fileCode;
    private Long userId;
    private String username;
}
