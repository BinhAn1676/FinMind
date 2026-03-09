package com.finance.keymanagementservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * No-op KMS service for local development when Google Cloud KMS is disabled.
 * Keys are stored without external encryption - NOT suitable for production.
 */
@Service
@Slf4j
@ConditionalOnProperty(name = "google.cloud.kms.enabled", havingValue = "false", matchIfMissing = true)
public class NoOpKmsService implements KmsService {

    @Override
    public String encrypt(String plaintext) {
        log.warn("KMS disabled - storing data without KMS encryption (local dev only, NOT for production)");
        return plaintext;
    }

    @Override
    public String decrypt(String encryptedData) {
        log.warn("KMS disabled - returning data without KMS decryption (local dev only, NOT for production)");
        return encryptedData;
    }

    @Override
    public String encryptLocalMasterKey(String localMasterKey) {
        return encrypt(localMasterKey);
    }

    @Override
    public String decryptLocalMasterKey(String encryptedLocalMasterKey) {
        return decrypt(encryptedLocalMasterKey);
    }
}
