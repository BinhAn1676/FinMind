package com.finance.keymanagementservice.common.cache.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
@RequiredArgsConstructor
public class RedisInfrasServiceImpl implements RedisInfrasService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    
    // Inject the ObjectMapper bean from JacksonConfig
    private final ObjectMapper objectMapper;
    
    @Override
    public void setString(String key, String value) {
        if (!StringUtils.hasLength(key)) { // null or ''
            log.debug("Key is empty, not setting string in Redis");
            return;
        }
        log.debug("Setting string in Redis with key: {}", key);
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public String getString(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key))
                .map(String::valueOf)
                .orElse(null);
    }

    @Override
    public void setObject(String key, Object value) {
        if (!StringUtils.hasLength(key)) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("setObject error:{}", e.getMessage());
        }
    }

    @Override
    public <T> T getObject(String key, Class<T> targetClass) {
        log.debug("Getting object from Redis with key: {}, class: {}", key, targetClass.getName());
        Object result = redisTemplate.opsForValue().get(key);
        
        if (result == null) {
            log.debug("No value found in Redis for key: {}", key);
            return null;
        }
        
        log.debug("Found value in Redis for key: {}, type: {}", key, result.getClass().getName());
        
        // If result is a Map
        if (result instanceof Map) {
            try {
                log.debug("Converting Map to target class: {}", targetClass.getName());
                return objectMapper.convertValue(result, targetClass);
            } catch (IllegalArgumentException e) {
                log.error("Error converting Map to object: {}", e.getMessage(), e);
                return null;
            }
        }

        // If result is a String
        if (result instanceof String) {
            try {
                log.debug("Deserializing String to target class: {}", targetClass.getName());
                return objectMapper.readValue((String) result, targetClass);
            } catch (JsonProcessingException e) {
                log.error("Error deserializing JSON to object: {}", e.getMessage(), e);
                return null;
            }
        }

        // Try direct cast if the object is already of the target type
        if (targetClass.isInstance(result)) {
            log.debug("Direct cast to target class: {}", targetClass.getName());
            return targetClass.cast(result);
        }

        log.debug("Could not convert result to target class: {}", targetClass.getName());
        return null;
    }

    @Override
    public void delete(String key) {
        redisTemplate.delete(key);
    }

    @Override
    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }

    @Override
    public void setInt(String key, int value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public int getInt(String key) {
        return (int) redisTemplate.opsForValue().get(key);
    }

    @Override
    public void setObjectWithTTL(String key, Object value, long timeout, TimeUnit unit) {
        if (!StringUtils.hasLength(key)) {
            log.debug("Key is empty, not setting value in Redis");
            return;
        }
        
        try {
            log.debug("Setting object in Redis with key: {}, type: {}, TTL: {} {}", 
                     key, value.getClass().getName(), timeout, unit);
            redisTemplate.opsForValue().set(key, value, timeout, unit);
            log.debug("Successfully set value in Redis for key: {}", key);
        } catch (Exception e) {
            log.error("Error setting object with TTL in Redis: {}", e.getMessage(), e);
        }
    }

    @Override
    public void setObjectWithTTL(String key, Object value, long timeoutSeconds) {
        setObjectWithTTL(key, value, timeoutSeconds, TimeUnit.SECONDS);
    }
}
