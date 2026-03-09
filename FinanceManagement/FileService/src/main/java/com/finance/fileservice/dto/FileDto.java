package com.finance.fileservice.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileDto {
    
    private String id;
    
    @NotEmpty(message = "User ID cannot be empty")
    private String userId;
    
    @NotEmpty(message = "Purpose cannot be empty")
    private String purpose;
    
    private String fileName;
    private String originalFileName;
    private String fileType;
    private Long fileSize;
    private String fileUrl;
    private String liveUrl; // Presigned URL for temporary access
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
