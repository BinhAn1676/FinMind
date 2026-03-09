package com.finance.keymanagementservice.service;

import com.google.api.gax.rpc.DeadlineExceededException;
import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.EncryptRequest;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.cloud.kms.v1.DecryptRequest;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.protobuf.ByteString;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "google.cloud.kms.enabled", havingValue = "true")
public class GoogleCloudKmsService implements KmsService {
    
    @Value("${google.cloud.kms.project-id}")
    private String projectId;
    
    @Value("${google.cloud.kms.location}")
    private String location;
    
    @Value("${google.cloud.kms.key-ring}")
    private String keyRing;
    
    @Value("${google.cloud.kms.crypto-key}")
    private String cryptoKey;
    
    @Value("${google.cloud.kms.retry-attempts:3}")
    private int retryAttempts;
    
    private final KeyManagementServiceClient kmsClient;
    
    /**
     * Encrypt data using Google Cloud KMS with retry logic
     */
    public String encrypt(String plaintext) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                log.debug("Encrypting data with Google Cloud KMS (attempt {}/{})", attempt, retryAttempts);
                
                CryptoKeyName keyName = CryptoKeyName.of(projectId, location, keyRing, cryptoKey);
                
                EncryptRequest request = EncryptRequest.newBuilder()
                        .setName(keyName.toString())
                        .setPlaintext(ByteString.copyFrom(plaintext, StandardCharsets.UTF_8))
                        .build();
                
                EncryptResponse response = kmsClient.encrypt(request);
                
                String encryptedData = Base64.getEncoder().encodeToString(
                        response.getCiphertext().toByteArray()
                );
                
                log.debug("Successfully encrypted data with KMS");
                return encryptedData;
                
            } catch (DeadlineExceededException e) {
                lastException = e;
                log.warn("KMS encrypt timeout on attempt {}/{}: {}", attempt, retryAttempts, e.getMessage());
                
                if (attempt < retryAttempts) {
                    long delayMs = (long) Math.pow(2, attempt) * 1000; // Exponential backoff
                    log.info("Retrying after {} ms...", delayMs);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry delay", ie);
                    }
                } else {
                    log.error("All {} encrypt attempts failed with timeout", retryAttempts);
                }
            } catch (Exception e) {
                log.error("Error encrypting data with Google Cloud KMS on attempt {}/{}: {}", 
                        attempt, retryAttempts, e.getMessage(), e);
                throw new RuntimeException("Failed to encrypt data with KMS", e);
            }
        }
        
        // If we get here, all retries failed
        log.error("Failed to encrypt data with KMS after {} attempts", retryAttempts);
        throw new RuntimeException("Failed to encrypt data with KMS after " + retryAttempts + " attempts", lastException);
    }
    
    /**
     * Decrypt data using Google Cloud KMS with retry logic
     */
    public String decrypt(String encryptedData) {
        Exception lastException = null;
        
        for (int attempt = 1; attempt <= retryAttempts; attempt++) {
            try {
                log.debug("Decrypting data with Google Cloud KMS (attempt {}/{})", attempt, retryAttempts);
                
                CryptoKeyName keyName = CryptoKeyName.of(projectId, location, keyRing, cryptoKey);
                
                byte[] ciphertext = Base64.getDecoder().decode(encryptedData);
                
                DecryptRequest request = DecryptRequest.newBuilder()
                        .setName(keyName.toString())
                        .setCiphertext(ByteString.copyFrom(ciphertext))
                        .build();
                
                DecryptResponse response = kmsClient.decrypt(request);
                
                String plaintext = response.getPlaintext().toString(StandardCharsets.UTF_8);
                
                log.debug("Successfully decrypted data with KMS");
                return plaintext;
                
            } catch (DeadlineExceededException e) {
                lastException = e;
                log.warn("KMS decrypt timeout on attempt {}/{}: {}", attempt, retryAttempts, e.getMessage());
                
                if (attempt < retryAttempts) {
                    long delayMs = (long) Math.pow(2, attempt) * 1000; // Exponential backoff
                    log.info("Retrying after {} ms...", delayMs);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Interrupted during retry delay", ie);
                    }
                } else {
                    log.error("All {} decrypt attempts failed with timeout", retryAttempts);
                }
            } catch (Exception e) {
                log.error("Error decrypting data with Google Cloud KMS on attempt {}/{}: {}", 
                        attempt, retryAttempts, e.getMessage(), e);
                throw new RuntimeException("Failed to decrypt data with KMS", e);
            }
        }
        
        // If we get here, all retries failed
        log.error("Failed to decrypt data with KMS after {} attempts", retryAttempts);
        throw new RuntimeException("Failed to decrypt data with KMS after " + retryAttempts + " attempts", lastException);
    }
    
    /**
     * Encrypt local master key using Google Cloud KMS
     */
    public String encryptLocalMasterKey(String localMasterKey) {
        try {
            log.debug("Encrypting local master key with Google Cloud KMS");
            return encrypt(localMasterKey);
        } catch (Exception e) {
            log.error("Error encrypting local master key with Google Cloud KMS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to encrypt local master key with KMS", e);
        }
    }
    
    /**
     * Decrypt local master key using Google Cloud KMS
     */
    public String decryptLocalMasterKey(String encryptedLocalMasterKey) {
        try {
            log.debug("Decrypting local master key with Google Cloud KMS");
            return decrypt(encryptedLocalMasterKey);
        } catch (Exception e) {
            log.error("Error decrypting local master key with Google Cloud KMS: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to decrypt local master key with KMS", e);
        }
    }
}
