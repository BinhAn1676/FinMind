package com.finance.fileservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FileUploadRequest {
    
    @NotEmpty(message = "User ID cannot be empty")
    private String userId;
    
    @NotEmpty(message = "Purpose cannot be empty")
    private String purpose;
    
    @NotNull(message = "File cannot be null")
    private MultipartFile file;
    
    private String description;
}

