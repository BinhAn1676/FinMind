package com.finance.fileservice.service;

import com.finance.fileservice.dto.FileDto;
import com.finance.fileservice.dto.FileUploadRequest;
import com.finance.fileservice.dto.FileDto;
import com.finance.fileservice.dto.FileUploadRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.Page;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

public interface FileService {
    
    FileDto uploadFile(FileUploadRequest request);
    
    FileDto getFileById(String id);
    
    FileDto getFileByUserIdAndPurpose(String userId, String purpose);
    
    Page<FileDto> getFilesByUserId(String userId, int page, int size);
    
    Page<FileDto> getFilesByUserIdAndPurpose(String userId, String purpose, int page, int size);
    
    String getLiveUrl(String fileId, int expiryTimeInSeconds);
    
    String getLiveUrlByUserIdAndPurpose(String userId, String purpose, int expiryTimeInSeconds);

    Map<String, String> getLiveUrls(List<String> fileIds, int expiryTimeInSeconds);
    
    void downloadFile(String fileId, HttpServletResponse response);
    
    void downloadFileByUserIdAndPurpose(String userId, String purpose, HttpServletResponse response);
    
    boolean deleteFile(String fileId);
    
    boolean deleteFileByUserIdAndPurpose(String userId, String purpose);
    
    InputStream getFileStream(String fileId);
    
    InputStream getFileStreamByUserIdAndPurpose(String userId, String purpose);
}