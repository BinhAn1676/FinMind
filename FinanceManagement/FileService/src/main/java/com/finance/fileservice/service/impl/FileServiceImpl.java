package com.finance.fileservice.service.impl;

import com.finance.fileservice.dto.FileDto;
import com.finance.fileservice.dto.FileUploadRequest;
import com.finance.fileservice.entity.FileEntity;
import com.finance.fileservice.exception.FileNotFoundException;
import com.finance.fileservice.exception.FileServiceException;
import com.finance.fileservice.repository.FileRepository;
import com.finance.fileservice.service.FileService;
import com.finance.fileservice.service.MinioService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileServiceImpl implements FileService {
    
    private final FileRepository fileRepository;
    private final MinioService minioService;
    
    private static final int DEFAULT_EXPIRY_TIME = 3600; // 1 hour

    @Override
    @Transactional
    public FileDto uploadFile(FileUploadRequest request) {
        try {
            MultipartFile file = request.getFile();
            String userId = request.getUserId();
            String purpose = request.getPurpose();
            
            // Validate file
            if (file.isEmpty()) {
                throw new FileServiceException("File cannot be empty");
            }
            
            // Generate unique file name
            String originalFileName = file.getOriginalFilename();
            String fileExtension = StringUtils.getFilenameExtension(originalFileName);
            String uniqueFileName = generateUniqueFileName(userId, purpose, fileExtension);
            log.info("Generated unique file name: {}", uniqueFileName);

            // FIX: Only overwrite for avatar purposes, never for chat attachments
            // Chat attachments should always create new entities to preserve message history
            boolean shouldOverwrite = purpose != null &&
                    (purpose.equals("avatar") || purpose.equals("GROUP_AVATAR"));

            // Check if file with same userId and purpose already exists
            FileEntity existingFile = shouldOverwrite
                    ? fileRepository.findByUserIdAndPurpose(userId, purpose).orElse(null)
                    : null;

            FileEntity fileEntity;
            if (existingFile != null) {
                // Update existing file (overwrite) - ONLY for avatars
                log.info("Overwriting existing file for userId: {} and purpose: {}", userId, purpose);

                // Delete old file from MinIO
                minioService.deleteFile(existingFile.getFileUrl());

                // Update existing entity
                existingFile.setFileName(uniqueFileName);
                existingFile.setOriginalFileName(originalFileName);
                existingFile.setFileType(file.getContentType());
                existingFile.setFileSize(file.getSize());
                existingFile.setFileUrl(uniqueFileName);
                existingFile.setDescription(request.getDescription());
                existingFile.setUpdatedAt(LocalDateTime.now());

                fileEntity = existingFile;
            } else {
                // Create new file entity (always for CHAT_ATTACHMENT, first-time for avatars)
                fileEntity = FileEntity.builder()
                        .userId(userId)
                        .purpose(purpose)
                        .fileName(uniqueFileName)
                        .originalFileName(originalFileName)
                        .fileType(file.getContentType())
                        .fileSize(file.getSize())
                        .fileUrl(uniqueFileName)
                        .description(request.getDescription())
                        .build();
            }
            
            // Upload file to MinIO
            log.info("Uploading file to MinIO with object name: {}", uniqueFileName);
            boolean uploadSuccess = minioService.uploadFile(uniqueFileName, file);
            if (!uploadSuccess) {
                throw new FileServiceException("Failed to upload file to MinIO");
            }
            log.info("File uploaded successfully to MinIO");
            
            // Save to database
            FileEntity savedFile = fileRepository.save(fileEntity);
            
            log.info("File uploaded successfully: {} for userId: {} and purpose: {}", 
                    uniqueFileName, userId, purpose);
            
            return mapToDto(savedFile);
            
        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            throw new FileServiceException("Failed to upload file: " + e.getMessage());
        }
    }

    @Override
    public FileDto getFileById(String id) {
        FileEntity fileEntity = fileRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("File not found with id: " + id));
        return mapToDto(fileEntity);
    }

    @Override
    public FileDto getFileByUserIdAndPurpose(String userId, String purpose) {
        FileEntity fileEntity = fileRepository.findByUserIdAndPurpose(userId, purpose)
                .orElseThrow(() -> new FileNotFoundException("File not found for userId: " + userId + " and purpose: " + purpose));
        return mapToDto(fileEntity);
    }

    @Override
    public Page<FileDto> getFilesByUserId(String userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FileEntity> fileEntities = fileRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return fileEntities.map(this::mapToDto);
    }

    @Override
    public Page<FileDto> getFilesByUserIdAndPurpose(String userId, String purpose, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FileEntity> fileEntities = fileRepository.findByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose, pageable);
        return fileEntities.map(this::mapToDto);
    }

    @Override
    public String getLiveUrl(String fileId, int expiryTimeInSeconds) {
        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException("File not found with id: " + fileId));
        log.info("Getting live URL for fileId: {}, fileUrl: {}", fileId, fileEntity.getFileUrl());
        return minioService.generatePresignedUrl(fileEntity.getFileUrl(), expiryTimeInSeconds);
    }

    @Override
    public String getLiveUrlByUserIdAndPurpose(String userId, String purpose, int expiryTimeInSeconds) {
        FileEntity fileEntity = fileRepository.findByUserIdAndPurpose(userId, purpose)
                .orElseThrow(() -> new FileNotFoundException("File not found for userId: " + userId + " and purpose: " + purpose));
        return minioService.generatePresignedUrl(fileEntity.getFileUrl(), expiryTimeInSeconds);
    }

    @Override
    public Map<String, String> getLiveUrls(List<String> fileIds, int expiryTimeInSeconds) {
        Map<String, String> result = new HashMap<>();
        if (fileIds == null || fileIds.isEmpty()) {
            return result;
        }
        for (String fileId : fileIds) {
            if (!StringUtils.hasText(fileId)) {
                continue;
            }
            try {
                String liveUrl = getLiveUrl(fileId, expiryTimeInSeconds);
                result.put(fileId, liveUrl);
            } catch (Exception ex) {
                log.warn("Unable to generate live url for fileId {}: {}", fileId, ex.getMessage());
            }
        }
        return result;
    }

    @Override
    public void downloadFile(String fileId, HttpServletResponse response) {
        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException("File not found with id: " + fileId));
        
        try (InputStream inputStream = minioService.getFileAsStream(fileEntity.getFileUrl())) {
            response.setContentType(fileEntity.getFileType());
            response.setContentLengthLong(fileEntity.getFileSize());
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileEntity.getOriginalFileName() + "\"");
            
            try (OutputStream outputStream = response.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
        } catch (IOException e) {
            log.error("Error downloading file: {}", e.getMessage(), e);
            throw new FileServiceException("Failed to download file: " + e.getMessage());
        }
    }

    @Override
    public void downloadFileByUserIdAndPurpose(String userId, String purpose, HttpServletResponse response) {
        FileEntity fileEntity = fileRepository.findByUserIdAndPurpose(userId, purpose)
                .orElseThrow(() -> new FileNotFoundException("File not found for userId: " + userId + " and purpose: " + purpose));
        
        try (InputStream inputStream = minioService.getFileAsStream(fileEntity.getFileUrl())) {
            response.setContentType(fileEntity.getFileType());
            response.setContentLengthLong(fileEntity.getFileSize());
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileEntity.getOriginalFileName() + "\"");
            
            try (OutputStream outputStream = response.getOutputStream()) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
        } catch (IOException e) {
            log.error("Error downloading file: {}", e.getMessage(), e);
            throw new FileServiceException("Failed to download file: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean deleteFile(String fileId) {
        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException("File not found with id: " + fileId));
        
        try {
            // Delete from MinIO
            boolean deletedFromMinIO = minioService.deleteFile(fileEntity.getFileUrl());
            if (!deletedFromMinIO) {
                log.warn("Failed to delete file from MinIO: {}", fileEntity.getFileUrl());
            }
            
            // Delete from database
            fileRepository.deleteById(fileId);
            
            log.info("File deleted successfully: {} for userId: {} and purpose: {}", 
                    fileEntity.getFileName(), fileEntity.getUserId(), fileEntity.getPurpose());
            return true;
            
        } catch (Exception e) {
            log.error("Error deleting file: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean deleteFileByUserIdAndPurpose(String userId, String purpose) {
        FileEntity fileEntity = fileRepository.findByUserIdAndPurpose(userId, purpose)
                .orElseThrow(() -> new FileNotFoundException("File not found for userId: " + userId + " and purpose: " + purpose));
        
        try {
            // Delete from MinIO
            boolean deletedFromMinIO = minioService.deleteFile(fileEntity.getFileUrl());
            if (!deletedFromMinIO) {
                log.warn("Failed to delete file from MinIO: {}", fileEntity.getFileUrl());
            }
            
            // Delete from database
            fileRepository.deleteById(fileEntity.getId());
            
            log.info("File deleted successfully: {} for userId: {} and purpose: {}", 
                    fileEntity.getFileName(), fileEntity.getUserId(), fileEntity.getPurpose());
            return true;
            
        } catch (Exception e) {
            log.error("Error deleting file: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public InputStream getFileStream(String fileId) {
        FileEntity fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new FileNotFoundException("File not found with id: " + fileId));
        return minioService.getFileAsStream(fileEntity.getFileUrl());
    }

    @Override
    public InputStream getFileStreamByUserIdAndPurpose(String userId, String purpose) {
        FileEntity fileEntity = fileRepository.findByUserIdAndPurpose(userId, purpose)
                .orElseThrow(() -> new FileNotFoundException("File not found for userId: " + userId + " and purpose: " + purpose));
        return minioService.getFileAsStream(fileEntity.getFileUrl());
    }
    
    private String generateUniqueFileName(String userId, String purpose, String fileExtension) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return String.format("%s/%s/%s_%s.%s", userId, purpose, timestamp, uuid, fileExtension);
    }
    
    private FileDto mapToDto(FileEntity fileEntity) {
        FileDto dto = new FileDto();
        dto.setId(fileEntity.getId());
        dto.setUserId(fileEntity.getUserId());
        dto.setPurpose(fileEntity.getPurpose());
        dto.setFileName(fileEntity.getFileName());
        dto.setOriginalFileName(fileEntity.getOriginalFileName());
        dto.setFileType(fileEntity.getFileType());
        dto.setFileSize(fileEntity.getFileSize());
        dto.setFileUrl(fileEntity.getFileUrl());
        dto.setDescription(fileEntity.getDescription());
        dto.setCreatedAt(fileEntity.getCreatedAt());
        dto.setUpdatedAt(fileEntity.getUpdatedAt());
        return dto;
    }
}
