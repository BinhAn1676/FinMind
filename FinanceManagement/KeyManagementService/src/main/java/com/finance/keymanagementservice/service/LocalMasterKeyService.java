package com.finance.keymanagementservice.service;

import com.finance.keymanagementservice.entity.LocalMasterKey;
import com.finance.keymanagementservice.repository.LocalMasterKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LocalMasterKeyService {
    
    private final LocalMasterKeyRepository localMasterKeyRepository;
    private final KmsService kmsService;
    private final AesEncryptionService aesEncryptionService;
    
    private static final String LOCAL_MASTER_KEY_NAME = "default-master-key";
    private static final String LOCAL_MASTER_KEY_VERSION = "v1";
    
    /**
     * Get or create local master key
     */
    @Transactional
    public String getOrCreateLocalMasterKey() {
        try {
            log.debug("Getting or creating local master key");
            
            // Try to get existing active local master key
            Optional<LocalMasterKey> existingKey = localMasterKeyRepository.findActiveByKeyName(LOCAL_MASTER_KEY_NAME);
            
            if (existingKey.isPresent()) {
                log.info("Found existing local master key (ID: {}), decrypting it", existingKey.get().getId());
                LocalMasterKey localMasterKeyEntity = existingKey.get();
                
                try {
                    // Decrypt local master key using Google Cloud KMS
                    String decryptedLocalMasterKey = kmsService.decryptLocalMasterKey(localMasterKeyEntity.getEncryptedMasterKey());
                    log.info("Successfully decrypted local master key with KMS");
                    return decryptedLocalMasterKey;
                } catch (Exception e) {
                    log.error("Failed to decrypt existing local master key with KMS: {}", e.getMessage(), e);
                    log.warn("This will cause data encrypted with the old key to become unrecoverable!");
                    throw new RuntimeException("Failed to decrypt existing local master key", e);
                }
            }
            
            log.debug("No existing local master key found, creating new one");
            
            // Generate new local master key
            String newLocalMasterKey = aesEncryptionService.generateAesKey();
            log.debug("Generated new local master key");
            
            // Encrypt local master key using Google Cloud KMS
            String encryptedLocalMasterKey = kmsService.encryptLocalMasterKey(newLocalMasterKey);
            log.debug("Encrypted local master key with Google Cloud KMS");
            
            // Save encrypted local master key to database
            LocalMasterKey localMasterKeyEntity = LocalMasterKey.builder()
                    .keyName(LOCAL_MASTER_KEY_NAME)
                    .encryptedMasterKey(encryptedLocalMasterKey)
                    .keyVersion(LOCAL_MASTER_KEY_VERSION)
                    .isActive(true)
                    .build();
            
            localMasterKeyRepository.save(localMasterKeyEntity);
            log.info("Successfully saved encrypted local master key to database");
            
            return newLocalMasterKey;
            
        } catch (Exception e) {
            log.error("Error getting or creating local master key: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get or create local master key", e);
        }
    }
    
    /**
     * Get current active local master key version
     */
    public String getActiveLocalMasterKeyVersion() {
        return LOCAL_MASTER_KEY_VERSION;
    }
    
    /**
     * Check if local master key exists and is decryptable
     */
    public boolean isLocalMasterKeyValid() {
        try {
            Optional<LocalMasterKey> existingKey = localMasterKeyRepository.findActiveByKeyName(LOCAL_MASTER_KEY_NAME);
            if (existingKey.isEmpty()) {
                log.info("No local master key found");
                return false;
            }
            
            // Try to decrypt it
            String decryptedKey = kmsService.decryptLocalMasterKey(existingKey.get().getEncryptedMasterKey());
            log.info("Local master key is valid and decryptable");
            return decryptedKey != null && !decryptedKey.isEmpty();
            
        } catch (Exception e) {
            log.error("Local master key validation failed: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Rotate local master key
     */
    @Transactional
    public String rotateLocalMasterKey() {
        try {
            log.info("Rotating local master key");
            
            // Deactivate all existing local master keys
            localMasterKeyRepository.deactivateAll();
            log.debug("Deactivated all existing local master keys");
            
            // Create new local master key
            String newLocalMasterKey = getOrCreateLocalMasterKey();
            log.info("Successfully rotated local master key");
            
            return newLocalMasterKey;
            
        } catch (Exception e) {
            log.error("Error rotating local master key: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to rotate local master key", e);
        }
    }
}

