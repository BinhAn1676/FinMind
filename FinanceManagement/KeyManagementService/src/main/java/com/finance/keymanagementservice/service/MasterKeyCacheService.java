package com.finance.keymanagementservice.service;

import com.finance.keymanagementservice.common.cache.redis.RedisInfrasService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.concurrent.TimeUnit;

import static com.finance.keymanagementservice.constant.KeyManagementConstants.Redis.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class MasterKeyCacheService {
    
    private final RedisInfrasService redisInfrasService;
    private final LocalMasterKeyService localMasterKeyService;
    
    /**
     * Get local master key from cache or database
     */
    public String getMasterKey() {
        try {
            // Try to get from cache first
            String masterKey = redisInfrasService.getString(MASTER_KEY_CACHE_KEY);
            
            if (!ObjectUtils.isEmpty(masterKey)) {
                log.debug("Local master key found in Redis cache");
                return masterKey;
            }
            
            log.debug("Local master key not found in cache, retrieving from database");
            
            // Get from database (will create if not exists)
            masterKey = localMasterKeyService.getOrCreateLocalMasterKey();
            
            if (ObjectUtils.isEmpty(masterKey)) {
                log.error("Local master key is null from database");
                throw new RuntimeException("Local master key is null from database");
            }
            
            // Cache the local master key
            redisInfrasService.setObjectWithTTL(MASTER_KEY_CACHE_KEY, masterKey, MASTER_KEY_TTL);
            log.debug("Local master key cached in Redis with TTL: {} seconds", MASTER_KEY_TTL);
            
            return masterKey;
            
        } catch (Exception e) {
            log.error("Error getting local master key: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to get local master key", e);
        }
    }
    
    /**
     * Clear master key from cache
     */
    public void clearMasterKey() {
        try {
            redisInfrasService.delete(MASTER_KEY_CACHE_KEY);
            log.debug("Master key cleared from cache");
        } catch (Exception e) {
            log.error("Error clearing master key from cache: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Refresh local master key and update cache
     */
    public String refreshMasterKey() {
        try {
            log.debug("Refreshing local master key");
            
            // Clear existing cache
            clearMasterKey();
            
            // Get fresh local master key (will create new one if needed)
            String masterKey = localMasterKeyService.getOrCreateLocalMasterKey();
            
            if (ObjectUtils.isEmpty(masterKey)) {
                log.error("Local master key is null during refresh");
                throw new RuntimeException("Local master key is null during refresh");
            }
            
            // Cache the new local master key
            redisInfrasService.setObjectWithTTL(MASTER_KEY_CACHE_KEY, masterKey, MASTER_KEY_TTL);
            log.debug("Local master key refreshed and cached");
            
            return masterKey;
            
        } catch (Exception e) {
            log.error("Error refreshing local master key: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to refresh local master key", e);
        }
    }
}
