package com.finance.fileservice.service.impl;

import com.finance.fileservice.dto.properties.MinioProperties;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioServiceImpl implements com.finance.fileservice.service.MinioService {
    private final MinioClient minioClient;
    private final MinioProperties minioProperties;

    @Autowired
    @Qualifier("minioPublicClient")
    private MinioClient minioPublicClient;

    private void ensureBucketExists() {
        try {
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .build()
            );
            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(minioProperties.getBucketName())
                                .build()
                );
                log.info("Bucket '{}' created successfully.", minioProperties.getBucketName());
            }
        } catch (Exception e) {
            log.error("Error ensuring bucket exists: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to ensure bucket exists", e);
        }
    }

    @Override
    public boolean uploadFile(String uniqueFileName, MultipartFile file) {
        ensureBucketExists();
        try {
            log.info("Uploading file to MinIO bucket: {}, object: {}", minioProperties.getBucketName(), uniqueFileName);
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(uniqueFileName)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            log.info("File uploaded successfully to MinIO: {}", uniqueFileName);
            return true;
        } catch (Exception e) {
            log.error("Error uploading file to MinIO: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean uploadStream(String uniqueFileName, InputStream inputStream, long size, String contentType) {
        ensureBucketExists();
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(uniqueFileName)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
            return true;
        } catch (Exception e) {
            log.error("Error uploading stream to MinIO: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean deleteFile(String url) {
        ensureBucketExists();
        try {
            String objectName = url.substring(url.lastIndexOf("/") + 1);
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .build()
            );
            return true;
        } catch (Exception e) {
            log.error("Error deleting file from MinIO: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public ByteArrayOutputStream getFile(String objectName) {
        ensureBucketExists();
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            try (InputStream stream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .build())) {
                int nRead;
                byte[] data = new byte[1024];
                while ((nRead = stream.read(data, 0, data.length)) != -1) {
                    byteArrayOutputStream.write(data, 0, nRead);
                }
                byteArrayOutputStream.flush();
            }
            return byteArrayOutputStream;
        } catch (Exception e) {
            log.error("Error retrieving file from MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve file from MinIO", e);
        }
    }

    @Override
    public InputStream getFileAsStream(String objectName) {
        ensureBucketExists();
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            log.error("Error retrieving file stream from MinIO: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve file stream from MinIO", e);
        }
    }

    @Override
    public String generatePresignedUrl(String objectName, int expiryTimeInSeconds) {
        ensureBucketExists();
        try {
            log.info("Generating presigned URL for objectName: {}", objectName);
            // Use the full objectName path as stored in MinIO
            // Use public client so the signature is computed with the browser-accessible host
            String presignedUrl = minioPublicClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucketName())
                            .object(objectName)
                            .expiry(expiryTimeInSeconds, TimeUnit.SECONDS)
                            .build()
            );
            log.info("Generated presigned URL: {}", presignedUrl);
            return presignedUrl;
        } catch (Exception e) {
            log.error("Error generating presigned URL for object {}: {}", objectName, e.getMessage(), e);
            throw new RuntimeException("Failed to generate presigned URL", e);
        }
    }
}
