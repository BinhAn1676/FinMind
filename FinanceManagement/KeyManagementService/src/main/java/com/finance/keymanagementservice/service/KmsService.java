package com.finance.keymanagementservice.service;

public interface KmsService {
    String encrypt(String plaintext);
    String decrypt(String encryptedData);
    String encryptLocalMasterKey(String localMasterKey);
    String decryptLocalMasterKey(String encryptedLocalMasterKey);
}
