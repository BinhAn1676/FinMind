package com.finance.keymanagementservice.controller;

import com.finance.keymanagementservice.dto.*;
import com.finance.keymanagementservice.service.KeyManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/keys")
@RequiredArgsConstructor
@Slf4j
public class KeyManagementController {
    
    private final KeyManagementService keyManagementService;
    
    /**
     * Generate AES key for a user
     */
    @PostMapping("/generate")
    public ResponseEntity<GenerateKeyResponse> generateKey(@Valid @RequestBody GenerateKeyRequest request) {
        log.info("Received request to generate key for user: {}", request.getUserId());
        
        GenerateKeyResponse response = keyManagementService.generateKey(request);
        
        if (response.isSuccess()) {
            log.info("Successfully generated key for user: {}", request.getUserId());
            return ResponseEntity.ok(response);
        } else {
            log.warn("Failed to generate key for user: {} - {}", request.getUserId(), response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Encrypt data for a user
     */
    @PostMapping("/encrypt")
    public ResponseEntity<EncryptResponse> encrypt(@Valid @RequestBody EncryptRequest request) {
        log.info("Received request to encrypt data for user: {}", request.getUserId());
        
        EncryptResponse response = keyManagementService.encrypt(request);
        
        if (response.isSuccess()) {
            log.info("Successfully encrypted data for user: {}", request.getUserId());
            return ResponseEntity.ok(response);
        } else {
            log.warn("Failed to encrypt data for user: {} - {}", request.getUserId(), response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Decrypt data for a user
     */
    @PostMapping("/decrypt")
    public ResponseEntity<DecryptResponse> decrypt(@Valid @RequestBody DecryptRequest request) {
        log.info("Received request to decrypt data for user: {}", request.getUserId());
        
        DecryptResponse response = keyManagementService.decrypt(request);
        
        if (response.isSuccess()) {
            log.info("Successfully decrypted data for user: {}", request.getUserId());
            return ResponseEntity.ok(response);
        } else {
            log.warn("Failed to decrypt data for user: {} - {}", request.getUserId(), response.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
    
    /**
     * Deactivate user's AES key
     */
    @DeleteMapping("/{userId}")
    public ResponseEntity<String> deactivateUserKey(@PathVariable String userId) {
        log.info("Received request to deactivate key for user: {}", userId);
        
        boolean success = keyManagementService.deactivateUserKey(userId);
        
        if (success) {
            log.info("Successfully deactivated key for user: {}", userId);
            return ResponseEntity.ok("Key deactivated successfully");
        } else {
            log.warn("Failed to deactivate key for user: {}", userId);
            return ResponseEntity.badRequest().body("Failed to deactivate key");
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Key Management Service is running");
    }
    
    /**
     * Check local master key status
     */
    @GetMapping("/master-key/status")
    public ResponseEntity<String> checkMasterKeyStatus() {
        log.info("Checking local master key status");
        
        boolean isValid = keyManagementService.isLocalMasterKeyValid();
        
        if (isValid) {
            return ResponseEntity.ok("Local master key is valid and decryptable");
        } else {
            return ResponseEntity.badRequest().body("Local master key is invalid or cannot be decrypted");
        }
    }
    
    /**
     * Clear user AES key cache
     */
    @DeleteMapping("/{userId}/cache")
    public ResponseEntity<String> clearUserAesKeyCache(@PathVariable String userId) {
        log.info("Clearing user AES key cache for user: {}", userId);
        
        keyManagementService.clearUserAesKeyCache(userId);
        
        return ResponseEntity.ok("User AES key cache cleared successfully");
    }
}

