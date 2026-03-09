package com.finance.userservice.service.cache;

import com.finance.userservice.common.cache.redis.RedisInfrasService;
import com.finance.userservice.common.cache.redisson.RedisDistributedLocker;
import com.finance.userservice.common.cache.redisson.RedisDistributedService;
import com.finance.userservice.dto.user.UserDto;
import com.finance.userservice.service.crypto.PiiCryptoService;
import com.finance.userservice.exception.ResourceNotFoundException;
import com.finance.userservice.mapper.UserMapper;
import com.finance.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.concurrent.TimeUnit;

import static com.finance.userservice.constant.UserConstants.Redis.*;

@RequiredArgsConstructor
@Service
@Slf4j
public class UserCacheService {
    private final UserRepository userRepository;
    private final RedisInfrasService redisInfrasService;
    private final RedisDistributedService redisDistributedService;
    private final PiiCryptoService piiCryptoService;

    /**
     * Get user by username from cache or database.
     * If not found in cache, it will fetch from database and store in cache.
     *
     * @param username the username of the user
     * @return UserDto
     */
    public UserDto getUserDistributedCache(String username) {
        UserDto cachedUser = redisInfrasService.getObject(genUserCacheKey(username), UserDto.class);
        if(ObjectUtils.isEmpty(cachedUser)) {
            log.debug("User not found in Redis cache, fetching from database: {}", username);
            cachedUser = getUserFromDatabase(username);
        }
        return cachedUser;
    }

    private UserDto getUserFromDatabase(String username) {
        RedisDistributedLocker locker = redisDistributedService.getDistributedLock(genUserCacheLock(username));
        try {
            // 1 - Tao lock
            boolean isLock = locker.tryLock(1, 5, TimeUnit.SECONDS);
            // Lưu ý: Cho dù thành công hay không cũng phải unLock, bằng mọi giá.
            if (!isLock) {
                return null; // return retry
            }
            // Get cache
            UserDto cachedUser = redisInfrasService.getObject(genUserCacheKey(username), UserDto.class);
            // 2. YES
            if (!ObjectUtils.isEmpty(cachedUser)) {
                return cachedUser;
            }
            var user = userRepository.findByUsername(username).orElseThrow(
                    () -> new ResourceNotFoundException("User", "Username", username)
            );
            UserDto userDto = piiCryptoService.buildDecryptedUserDto(user);
            // set data to distributed cache
            redisInfrasService.setObjectWithTTL(genUserCacheKey(username), userDto, USER_CACHE_TTL);
            return userDto;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }finally {
            locker.unlock();
        }
    }

    private String genUserCacheKey(String username) {
        return USER_CACHE_KEY_PREFIX + username;
    }
    private String genUserCacheLock(String username) {
        return USER_LOCK_KEY_PREFIX + username;
    }
    

}
