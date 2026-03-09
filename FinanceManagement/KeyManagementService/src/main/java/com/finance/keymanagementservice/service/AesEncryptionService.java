package com.finance.keymanagementservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static com.finance.keymanagementservice.constant.KeyManagementConstants.Encryption.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AesEncryptionService {
    
    /**
     * Generate a new AES key
     */
    public String generateAesKey() {
        try {
            log.debug("Generating new AES key");
            
            KeyGenerator keyGenerator = KeyGenerator.getInstance(ALGORITHM);
            keyGenerator.init(KEY_LENGTH);
            SecretKey secretKey = keyGenerator.generateKey();
            
            String aesKey = Base64.getEncoder().encodeToString(secretKey.getEncoded());
            
            log.debug("Successfully generated AES key");
            return aesKey;
            
        } catch (Exception e) {
            log.error("Error generating AES key: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate AES key", e);
        }
    }
    
    /**
     * Encrypt data using AES
     */
    public String encrypt(String data, String aesKey) {
        try {
            log.debug("Encrypting data with AES");
            
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    Base64.getDecoder().decode(aesKey), ALGORITHM
            );
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            
            byte[] encryptedBytes = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            String encryptedData = Base64.getEncoder().encodeToString(encryptedBytes);
            
            log.debug("Successfully encrypted data with AES");
            return encryptedData;
            
        } catch (Exception e) {
            log.error("Error encrypting data with AES: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to encrypt data with AES", e);
        }
    }
    
    /**
     * Decrypt data using AES
     */
    public String decrypt(String encryptedData, String aesKey) {
        try {
            log.debug("Decrypting data with AES");
            
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    Base64.getDecoder().decode(aesKey), ALGORITHM
            );
            
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec);
            
            byte[] decryptedBytes = cipher.doFinal(
                    Base64.getDecoder().decode(encryptedData)
            );
            String decryptedData = new String(decryptedBytes, StandardCharsets.UTF_8);
            
            log.debug("Successfully decrypted data with AES");
            return decryptedData;
            
        } catch (Exception e) {
            log.error("Error decrypting data with AES: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to decrypt data with AES", e);
        }
    }
}
