package com.finance.keymanagementservice.service;

import com.finance.keymanagementservice.dto.*;
import com.finance.keymanagementservice.entity.AesKey;
import com.finance.keymanagementservice.repository.AesKeyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

import static com.finance.keymanagementservice.constant.KeyManagementConstants.Messages.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class KeyManagementService {
    
    private final AesKeyRepository aesKeyRepository;
    private final AesEncryptionService aesEncryptionService;
    private final MasterKeyCacheService masterKeyCacheService;
    private final LocalMasterKeyService localMasterKeyService;
    private final UserAesKeyCacheService userAesKeyCacheService;
    
    /**
     * Generate AES key for a user
     */
    @Transactional
    public GenerateKeyResponse generateKey(GenerateKeyRequest request) {
        try {
            log.info("Generating AES key for user: {}", request.getUserId());
            
            // Check if user already has an active key
            if (aesKeyRepository.existsActiveByUserId(request.getUserId())) {
                log.warn("User {} already has an active AES key", request.getUserId());
                return GenerateKeyResponse.builder()
                        .userId(request.getUserId())
                        .keyVersion(localMasterKeyService.getActiveLocalMasterKeyVersion())
                        .message(KEY_ALREADY_EXISTS)
                        .success(false)
                        .build();
            }
            
            // Generate new AES key
            String aesKey = aesEncryptionService.generateAesKey();
            log.debug("Generated AES key for user: {}", request.getUserId());
            
            // Get local master key from cache or database
            String localMasterKey = masterKeyCacheService.getMasterKey();
            if (localMasterKey == null) {
                log.error("Local master key is null for user: {}", request.getUserId());
                throw new RuntimeException(MASTER_KEY_NULL);
            }
            
            // Encrypt user AES key with local master key
            String encryptedAesKey = aesEncryptionService.encrypt(aesKey, localMasterKey);
            log.debug("Encrypted user AES key with local master key for user: {}", request.getUserId());
            
                // Save to database
                AesKey aesKeyEntity = AesKey.builder()
                        .userId(request.getUserId())
                        .encryptedAesKey(encryptedAesKey)
                        .keyVersion(localMasterKeyService.getActiveLocalMasterKeyVersion())
                        .isActive(true)
                        .build();
            
            aesKeyRepository.save(aesKeyEntity);
            log.info("Successfully saved AES key for user: {}", request.getUserId());
            
            return GenerateKeyResponse.builder()
                    .userId(request.getUserId())
                    .keyVersion(localMasterKeyService.getActiveLocalMasterKeyVersion())
                    .message(KEY_GENERATED_SUCCESS)
                    .success(true)
                    .build();
            
        } catch (Exception e) {
            log.error("Error generating AES key for user {}: {}", request.getUserId(), e.getMessage(), e);
            return GenerateKeyResponse.builder()
                    .userId(request.getUserId())
                    .keyVersion(localMasterKeyService.getActiveLocalMasterKeyVersion())
                    .message("Failed to generate AES key: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }
    
    /**
     * Encrypt data for a user
     */
    public EncryptResponse encrypt(EncryptRequest request) {
        try {
            log.info("Encrypting data for user: {}", request.getUserId());
            
            // Try to get user AES key from cache first
            String userAesKey = userAesKeyCacheService.getUserAesKey(request.getUserId());
            boolean keyWasCreated = false;
            
            if (userAesKey != null) {
                log.debug("User AES key found in cache for user: {}", request.getUserId());
            } else {
                log.debug("User AES key not found in cache, checking database for user: {}", request.getUserId());
                
                // Get user's AES key from database or create one if it doesn't exist
                Optional<AesKey> aesKeyOpt = aesKeyRepository.findActiveByUserId(request.getUserId());
                AesKey aesKeyEntity;
                
                if (aesKeyOpt.isEmpty()) {
                    log.info("No active AES key found for user: {}, creating new one", request.getUserId());
                    keyWasCreated = true;
                    
                    // Create new AES key for user
                    userAesKey = aesEncryptionService.generateAesKey();
                    log.debug("Generated AES key for user: {}", request.getUserId());
                    
                    // Get local master key from cache or database
                    String localMasterKey = masterKeyCacheService.getMasterKey();
                    if (localMasterKey == null) {
                        log.error("Local master key is null for user: {}", request.getUserId());
                        throw new RuntimeException(MASTER_KEY_NULL);
                    }
                    
                    // Encrypt user AES key with local master key
                    String encryptedAesKey = aesEncryptionService.encrypt(userAesKey, localMasterKey);
                    log.debug("Encrypted user AES key with local master key for user: {}", request.getUserId());
                    
                    // Save to database
                    aesKeyEntity = AesKey.builder()
                            .userId(request.getUserId())
                            .encryptedAesKey(encryptedAesKey)
                            .keyVersion(localMasterKeyService.getActiveLocalMasterKeyVersion())
                            .isActive(true)
                            .build();
                    
                    aesKeyRepository.save(aesKeyEntity);
                    log.info("Successfully created and saved AES key for user: {}", request.getUserId());
                } else {
                    aesKeyEntity = aesKeyOpt.get();
                    
                    // Get local master key from cache or database
                    String localMasterKey = masterKeyCacheService.getMasterKey();
                    if (localMasterKey == null) {
                        log.error("Local master key is null for user: {}", request.getUserId());
                        throw new RuntimeException(MASTER_KEY_NULL);
                    }
                    
                    // Decrypt user's AES key with local master key
                    log.info("Decrypting user AES key (ID: {}, Version: {}) with local master key", 
                            aesKeyEntity.getId(), aesKeyEntity.getKeyVersion());
                    userAesKey = aesEncryptionService.decrypt(aesKeyEntity.getEncryptedAesKey(), localMasterKey);
                    log.info("Successfully decrypted user AES key");
                }
                
                // Cache the decrypted user AES key
                userAesKeyCacheService.cacheUserAesKey(request.getUserId(), userAesKey);
                log.debug("User AES key cached for user: {}", request.getUserId());
            }
            
            // Encrypt data with user's AES key
            String encryptedData = aesEncryptionService.encrypt(request.getData(), userAesKey);
            log.info("Successfully encrypted data for user: {}", request.getUserId());
            
            // Check if key was auto-created
            String message = keyWasCreated ? 
                ENCRYPTION_SUCCESS + " (Key auto-created)" : 
                ENCRYPTION_SUCCESS;
            
            return EncryptResponse.builder()
                    .userId(request.getUserId())
                    .encryptedData(encryptedData)
                    .message(message)
                    .success(true)
                    .build();
            
        } catch (Exception e) {
            log.error("Error encrypting data for user {}: {}", request.getUserId(), e.getMessage(), e);
            return EncryptResponse.builder()
                    .userId(request.getUserId())
                    .message("Failed to encrypt data: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }
    
    /**
     * Decrypt data for a user
     */
    public DecryptResponse decrypt(DecryptRequest request) {
        try {
            log.info("Decrypting data for user: {}", request.getUserId());
            
            // Try to get user AES key from cache first
            String userAesKey = userAesKeyCacheService.getUserAesKey(request.getUserId());
            
            if (userAesKey == null) {
                log.debug("User AES key not found in cache, checking database for user: {}", request.getUserId());
                
                // Get user's AES key from database
                Optional<AesKey> aesKeyOpt = aesKeyRepository.findActiveByUserId(request.getUserId());
                if (aesKeyOpt.isEmpty()) {
                    log.warn("No active AES key found for user: {}", request.getUserId());
                    return DecryptResponse.builder()
                            .userId(request.getUserId())
                            .message("No active AES key found for user. Please generate a key first using /api/v1/keys/generate")
                            .success(false)
                            .build();
                }
                
                AesKey aesKeyEntity = aesKeyOpt.get();
                
                // Get local master key from cache or database
                String localMasterKey = masterKeyCacheService.getMasterKey();
                if (localMasterKey == null) {
                    log.error("Local master key is null for user: {}", request.getUserId());
                    throw new RuntimeException(MASTER_KEY_NULL);
                }
                
                // Decrypt user's AES key with local master key
                log.info("Decrypting user AES key (ID: {}, Version: {}) with local master key", 
                        aesKeyEntity.getId(), aesKeyEntity.getKeyVersion());
                userAesKey = aesEncryptionService.decrypt(aesKeyEntity.getEncryptedAesKey(), localMasterKey);
                log.info("Successfully decrypted user AES key");
                
                // Cache the decrypted user AES key
                userAesKeyCacheService.cacheUserAesKey(request.getUserId(), userAesKey);
                log.debug("User AES key cached for user: {}", request.getUserId());
            } else {
                log.debug("User AES key found in cache for user: {}", request.getUserId());
            }
            
            // Decrypt data with user's AES key
            String decryptedData = aesEncryptionService.decrypt(request.getEncryptedData(), userAesKey);
            log.info("Successfully decrypted data for user: {}", request.getUserId());
            
            return DecryptResponse.builder()
                    .userId(request.getUserId())
                    .decryptedData(decryptedData)
                    .message(DECRYPTION_SUCCESS)
                    .success(true)
                    .build();
            
        } catch (Exception e) {
            log.error("Error decrypting data for user {}: {}", request.getUserId(), e.getMessage(), e);
            return DecryptResponse.builder()
                    .userId(request.getUserId())
                    .message("Failed to decrypt data: " + e.getMessage())
                    .success(false)
                    .build();
        }
    }
    
    /**
     * Deactivate user's AES key
     */
    @Transactional
    public boolean deactivateUserKey(String userId) {
        try {
            log.info("Deactivating AES key for user: {}", userId);
            
            aesKeyRepository.deactivateAllByUserId(userId);
            
            // Clear user AES key from cache
            userAesKeyCacheService.clearUserAesKey(userId);
            log.debug("Cleared user AES key from cache for user: {}", userId);
            
            log.info("Successfully deactivated AES key for user: {}", userId);
            
            return true;
            
        } catch (Exception e) {
            log.error("Error deactivating AES key for user {}: {}", userId, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Check if local master key is valid
     */
    public boolean isLocalMasterKeyValid() {
        return localMasterKeyService.isLocalMasterKeyValid();
    }
    
    /**
     * Clear user AES key cache
     */
    public void clearUserAesKeyCache(String userId) {
        userAesKeyCacheService.clearUserAesKey(userId);
    }
    

}
