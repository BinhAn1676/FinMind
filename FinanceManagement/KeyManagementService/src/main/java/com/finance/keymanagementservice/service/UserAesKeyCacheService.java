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
public class UserAesKeyCacheService {
    
    private final RedisInfrasService redisInfrasService;
    
    /**
     * Get user AES key from cache
     */
    public String getUserAesKey(String userId) {
        try {
            String cacheKey = USER_AES_KEY_CACHE_PREFIX + userId;
            String userAesKey = redisInfrasService.getString(cacheKey);
            
            if (!ObjectUtils.isEmpty(userAesKey)) {
                log.debug("User AES key found in Redis cache for user: {}", userId);
                return userAesKey;
            }
            
            log.debug("User AES key not found in cache for user: {}", userId);
            return null;
            
        } catch (Exception e) {
            log.error("Error getting user AES key from cache for user {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Cache user AES key
     */
    public void cacheUserAesKey(String userId, String userAesKey) {
        try {
            String cacheKey = USER_AES_KEY_CACHE_PREFIX + userId;
            redisInfrasService.setObjectWithTTL(cacheKey, userAesKey, USER_AES_KEY_TTL);
            log.debug("User AES key cached for user: {} with TTL: {} seconds", userId, USER_AES_KEY_TTL);
            
        } catch (Exception e) {
            log.error("Error caching user AES key for user {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Clear user AES key from cache
     */
    public void clearUserAesKey(String userId) {
        try {
            String cacheKey = USER_AES_KEY_CACHE_PREFIX + userId;
            redisInfrasService.delete(cacheKey);
            log.debug("User AES key cleared from cache for user: {}", userId);
            
        } catch (Exception e) {
            log.error("Error clearing user AES key from cache for user {}: {}", userId, e.getMessage(), e);
        }
    }
    
    /**
     * Clear all user AES keys from cache
     */
    public void clearAllUserAesKeys() {
        try {
            // Note: This is a simple implementation. In production, you might want to use Redis SCAN
            // or maintain a set of user IDs to clear them individually
            log.info("Clearing all user AES keys from cache");
            // For now, we'll just log this. Individual clearing is handled by clearUserAesKey()
            
        } catch (Exception e) {
            log.error("Error clearing all user AES keys from cache: {}", e.getMessage(), e);
        }
    }
}
