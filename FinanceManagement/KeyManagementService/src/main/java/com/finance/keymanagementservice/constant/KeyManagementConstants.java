package com.finance.keymanagementservice.constant;

public class KeyManagementConstants {
    
    public static class Redis {
        public static final String MASTER_KEY_CACHE_KEY = "master_key";
        public static final long MASTER_KEY_TTL = 3600; // 1 hour in seconds
        
        public static final String USER_AES_KEY_CACHE_PREFIX = "user_aes_key:";
        public static final long USER_AES_KEY_TTL = 600; // 10 minutes in seconds
    }
    
    public static class Encryption {
        public static final String ALGORITHM = "AES";
        public static final String TRANSFORMATION = "AES";
        public static final int KEY_LENGTH = 256;
    }
    
    public static class Messages {
        public static final String KEY_GENERATED_SUCCESS = "AES key generated successfully";
        public static final String KEY_ALREADY_EXISTS = "User already has an active AES key";
        public static final String NO_ACTIVE_KEY = "No active AES key found for user";
        public static final String ENCRYPTION_SUCCESS = "Data encrypted successfully";
        public static final String DECRYPTION_SUCCESS = "Data decrypted successfully";
        public static final String KEY_DEACTIVATED_SUCCESS = "Key deactivated successfully";
        public static final String MASTER_KEY_NULL = "Master key is null";
        public static final String KEY_AUTO_CREATED = "AES key automatically created for user";
    }
}

