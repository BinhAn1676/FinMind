package com.finance.userservice.dto.file;

import com.finance.userservice.constant.FileCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDto {
    private String id;
    private String fileName;
    private String fileType;
    private FileCode fileCode;
    private String fileUrl;
    private String liveUrl;
} 