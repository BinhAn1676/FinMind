package com.finance.aiservice.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finance.aiservice.dto.CategoryDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for reading user categories directly from Redis cache.
 *
 * This service reads from the same Redis cache that FinanceService writes to,
 * avoiding HTTP calls and providing faster access to category data.
 *
 * Cache Strategy:
 * - FinanceService manages the cache (writes, updates, evictions)
 * - AIService only reads from the cache (no writes)
 * - Cache key format: "userCategories::userId"
 * - If cache miss, returns null (caller should handle by calling FinanceService)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_NAME = "userCategories";

    /**
     * Get user categories from Redis cache.
     *
     * @param userId User ID
     * @return List of categories if cached, null if cache miss
     */
    public List<CategoryDto> getCachedCategories(String userId) {
        try {
            String cacheKey = CACHE_NAME + "::" + userId;

            log.debug("Fetching categories from Redis cache for user: {}", userId);
            Object cachedValue = redisTemplate.opsForValue().get(cacheKey);

            if (cachedValue == null) {
                log.debug("Cache miss for user: {}", userId);
                return null;
            }

            // Convert cached value to List<CategoryDto>
            List<CategoryDto> categories = objectMapper.convertValue(
                cachedValue,
                new TypeReference<List<CategoryDto>>() {}
            );

            log.info("Cache hit: Loaded {} categories for user {} from Redis", categories.size(), userId);
            return categories;

        } catch (Exception e) {
            log.error("Error reading categories from Redis cache for user {}: {}", userId, e.getMessage(), e);
            return null;
        }
    }
}
