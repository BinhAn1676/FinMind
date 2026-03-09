package com.finance.fileservice.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public interface MinioService {
    boolean uploadFile(String uniqueFileName, MultipartFile file);

    boolean deleteFile(String fileName);

    ByteArrayOutputStream getFile(String objectName);
    
    /**
     * Get file as input stream for efficient streaming of large files
     * @param objectName Name of the object in MinIO
     * @return InputStream of the file
     */
    InputStream getFileAsStream(String objectName);
    
    /**
     * Stream a file to MinIO from an input stream
     * @param uniqueFileName Name to save the file as
     * @param inputStream Source input stream
     * @param size Size of the file in bytes
     * @param contentType MIME type of the content
     * @return true if upload successful, false otherwise
     */
    boolean uploadStream(String uniqueFileName, InputStream inputStream, long size, String contentType);
    
    String generatePresignedUrl(String objectName, int expiryTimeInSeconds);
}

