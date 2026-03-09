package com.finance.fileservice.controller;

import com.finance.fileservice.constant.FileConstants;
import com.finance.fileservice.dto.BulkLiveUrlRequest;
import com.finance.fileservice.dto.FileDto;
import com.finance.fileservice.dto.FileUploadRequest;
import com.finance.fileservice.dto.ResponseDto;
import com.finance.fileservice.service.FileService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
@Validated
public class FileController {
    
    private final FileService fileService;
    
    private static final String STATUS_200 = "200";
    private static final String MESSAGE_200 = "Request processed successfully";
    private static final String STATUS_201 = "201";
    private static final String MESSAGE_201 = "File uploaded successfully";
    private static final String STATUS_404 = "404";
    private static final String MESSAGE_404 = "File not found";
    private static final String STATUS_500 = "500";
    private static final String MESSAGE_500 = "Internal server error";

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(@Valid @ModelAttribute FileUploadRequest request) {
        try {
            FileDto fileDto = fileService.uploadFile(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(fileDto);
        } catch (Exception e) {
            log.error("Error uploading file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(STATUS_500, "Failed to upload file: " + e.getMessage()));
        }
    }

    @PostMapping("/live-url/bulk")
    public ResponseEntity<?> getLiveUrlsBulk(@RequestBody BulkLiveUrlRequest request) {
        try {
            if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
                return ResponseEntity.ok(Collections.emptyMap());
            }
            int expiry = request.getExpiry() != null ? request.getExpiry() : FileConstants.expiryTimeInSeconds;
            Map<String, String> liveUrls = fileService.getLiveUrls(request.getIds(), expiry);
            return ResponseEntity.ok(liveUrls);
        } catch (Exception e) {
            log.error("Error getting live URLs in bulk: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(STATUS_500, "Failed to get live URLs"));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getFileById(@PathVariable("id") @NotEmpty String id) {
        try {
            FileDto fileDto = fileService.getFileById(id);
            return ResponseEntity.ok(fileDto);
        } catch (Exception e) {
            log.error("Error getting file by id: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto(STATUS_404, MESSAGE_404));
        }
    }

    @GetMapping("/user/{userId}/purpose/{purpose}")
    public ResponseEntity<?> getFileByUserIdAndPurpose(
            @PathVariable("userId") @NotEmpty String userId,
            @PathVariable("purpose") @NotEmpty String purpose) {
        try {
            FileDto fileDto = fileService.getFileByUserIdAndPurpose(userId, purpose);
            return ResponseEntity.ok(fileDto);
        } catch (Exception e) {
            log.error("Error getting file by userId and purpose: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto(STATUS_404, MESSAGE_404));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getFilesByUserId(
            @PathVariable("userId") @NotEmpty String userId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            Page<FileDto> files = fileService.getFilesByUserId(userId, page, size);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            log.error("Error getting files by userId: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(STATUS_500, MESSAGE_500));
        }
    }

    @GetMapping("/user/{userId}/purpose/{purpose}/list")
    public ResponseEntity<?> getFilesByUserIdAndPurpose(
            @PathVariable("userId") @NotEmpty String userId,
            @PathVariable("purpose") @NotEmpty String purpose,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        try {
            Page<FileDto> files = fileService.getFilesByUserIdAndPurpose(userId, purpose, page, size);
            return ResponseEntity.ok(files);
        } catch (Exception e) {
            log.error("Error getting files by userId and purpose: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ResponseDto(STATUS_500, MESSAGE_500));
        }
    }

    @GetMapping("/{id}/live-url")
    public ResponseEntity<?> getLiveUrl(
            @PathVariable("id") @NotEmpty String id,
            @RequestParam(value = "expiry", defaultValue = "3600") int expiryTimeInSeconds) {
        try {
            String liveUrl = fileService.getLiveUrl(id, expiryTimeInSeconds);
            return ResponseEntity.ok(Map.of("liveUrl", liveUrl));
        } catch (Exception e) {
            log.error("Error getting live URL: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto(STATUS_404, MESSAGE_404));
        }
    }

    @GetMapping("/user/{userId}/purpose/{purpose}/live-url")
    public ResponseEntity<?> getLiveUrlByUserIdAndPurpose(
            @PathVariable("userId") @NotEmpty String userId,
            @PathVariable("purpose") @NotEmpty String purpose,
            @RequestParam(value = "expiry", defaultValue = "3600") int expiryTimeInSeconds) {
        try {
            String liveUrl = fileService.getLiveUrlByUserIdAndPurpose(userId, purpose, expiryTimeInSeconds);
            return ResponseEntity.ok(Map.of("liveUrl", liveUrl));
        } catch (Exception e) {
            log.error("Error getting live URL by userId and purpose: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto(STATUS_404, MESSAGE_404));
        }
    }

    @GetMapping("/{id}/download")
    public void downloadFile(@PathVariable("id") @NotEmpty String id, HttpServletResponse response) {
        try {
            fileService.downloadFile(id, response);
        } catch (Exception e) {
            log.error("Error downloading file: {}", e.getMessage(), e);
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
            } catch (IOException ioException) {
                log.error("Error sending error response: {}", ioException.getMessage(), ioException);
            }
        }
    }

    @GetMapping("/user/{userId}/purpose/{purpose}/download")
    public void downloadFileByUserIdAndPurpose(
            @PathVariable("userId") @NotEmpty String userId,
            @PathVariable("purpose") @NotEmpty String purpose,
            HttpServletResponse response) {
        try {
            fileService.downloadFileByUserIdAndPurpose(userId, purpose, response);
        } catch (Exception e) {
            log.error("Error downloading file by userId and purpose: {}", e.getMessage(), e);
            try {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found");
            } catch (IOException ioException) {
                log.error("Error sending error response: {}", ioException.getMessage(), ioException);
            }
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFile(@PathVariable("id") @NotEmpty String id) {
        try {
            boolean deleted = fileService.deleteFile(id);
            if (deleted) {
                return ResponseEntity.ok(new ResponseDto(STATUS_200, "File deleted successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ResponseDto(STATUS_500, "Failed to delete file"));
            }
        } catch (Exception e) {
            log.error("Error deleting file: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto(STATUS_404, MESSAGE_404));
        }
    }

    @DeleteMapping("/user/{userId}/purpose/{purpose}")
    public ResponseEntity<?> deleteFileByUserIdAndPurpose(
            @PathVariable("userId") @NotEmpty String userId,
            @PathVariable("purpose") @NotEmpty String purpose) {
        try {
            boolean deleted = fileService.deleteFileByUserIdAndPurpose(userId, purpose);
            if (deleted) {
                return ResponseEntity.ok(new ResponseDto(STATUS_200, "File deleted successfully"));
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(new ResponseDto(STATUS_500, "Failed to delete file"));
            }
        } catch (Exception e) {
            log.error("Error deleting file by userId and purpose: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ResponseDto(STATUS_404, MESSAGE_404));
        }
    }
}
