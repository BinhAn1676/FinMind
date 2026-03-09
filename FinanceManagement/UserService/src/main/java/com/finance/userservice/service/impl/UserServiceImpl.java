package com.finance.userservice.service.impl;

import com.finance.userservice.common.cache.redis.RedisInfrasService;
import com.finance.userservice.common.utils.SecurityUtils;
import com.finance.userservice.dto.KeyEncryptRequest;
import com.finance.userservice.dto.KeyEncryptResponse;
import com.finance.userservice.dto.KeyDecryptRequest;
import com.finance.userservice.dto.KeyDecryptResponse;
import com.finance.userservice.dto.user.UserContactDto;
import com.finance.userservice.dto.user.UserDto;
import com.finance.userservice.entity.User;
import com.finance.userservice.exception.AuthenticationException;
import com.finance.userservice.exception.ResourceNotFoundException;
import com.finance.userservice.mapper.UserMapper;
import com.finance.userservice.repository.UserRepository;
import com.finance.userservice.service.UserService;
import com.finance.userservice.service.cache.UserCacheService;
import com.finance.userservice.service.client.KeyManagementServiceClient;
import com.finance.userservice.service.crypto.PiiCryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.finance.userservice.common.utils.HashUtils.sha256;
import static com.finance.userservice.constant.UserConstants.Redis.USER_CACHE_KEY_PREFIX;
import static com.finance.userservice.constant.UserConstants.Redis.USER_CACHE_TTL;


@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final RedisInfrasService redisInfrasService;
    private final UserCacheService userCacheService;
    private final KeyManagementServiceClient keyManagementServiceClient;
    private final PiiCryptoService piiCryptoService;
    


    @Override
    @Transactional
    public UserDto create(UserDto userDto) {
        // Persist minimal user to get ID without storing plaintext PII
        var user = new User();
        user.setUsername(userDto.getUsername());
        user.setEmail(null);
        user.setFullName(null);
        user.setPhone(null);
        user.setAddress(null);
        userRepository.saveAndFlush(user);

        // Encrypt and set PII using generated ID
        encryptAndSetUserFields(user, userDto.getEmail(), userDto.getFullName(), userDto.getPhone(), userDto.getAddress());
        return piiCryptoService.buildDecryptedUserDto(user);
    }

    @Override
    public UserDto getById(Long id) {
        var user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User", "Id", id.toString())
        );
        return piiCryptoService.buildDecryptedUserDto(user);
    }

    @Override
    @Transactional
    public UserDto update(Long id, UserDto userDto) {
        var user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User", "Id", id.toString())
        );
        userDto.setId(id);
        user = UserMapper.mapToExistingUser(userDto, user);
        encryptAndSetUserFields(user, userDto.getEmail(), userDto.getFullName(), userDto.getPhone(), userDto.getAddress());

        // Delete user from Redis cache if it exists
        redisInfrasService.delete(USER_CACHE_KEY_PREFIX + user.getUsername());

        return piiCryptoService.buildDecryptedUserDto(user);
    }

    @Override
    @Transactional
    public boolean delete(Long id) {
        var user = userRepository.findById(id).orElseThrow(
                () -> new ResourceNotFoundException("User", "Id", id.toString())
        );
        userRepository.deleteById(user.getId());
        return true;
    }

    @Override
    public UserDto getByUsername(String username) {
        var cached = userCacheService.getUserDistributedCache(username);
        if (cached != null) {
            return cached;
        }
        var user = userRepository.findByUsername(username).orElseThrow(
                () -> new ResourceNotFoundException("User", "username", username)
        );
        return piiCryptoService.buildDecryptedUserDto(user);
    }

    @Override
    public Page<UserDto> getUsers(String textSearch, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        var users = userRepository.searchUsers(textSearch, pageable);
        if (!users.isEmpty()) {
            var result = users.map(piiCryptoService::buildDecryptedUserDto);
            return result;
        }
        return Page.empty(pageable);

    }

//    @NotNull
//    private static String getObjectName(UserDto.AvatarFile file) {
//        return file.getFileUrl().substring(file.getFileUrl().lastIndexOf("/") + 1);
//    }

    @Override
    @Transactional
    public UserDto getUserInfoFromToken() {
        // Extract username from JWT token
        String username = SecurityUtils.getCurrentUsername();
        if (ObjectUtils.isEmpty(username)) {
            throw new AuthenticationException("No username found in token");
        }

        // Try to get user from cache first
        UserDto cachedUser = redisInfrasService.getObject(USER_CACHE_KEY_PREFIX + username, UserDto.class);
        if (!ObjectUtils.isEmpty(cachedUser)) {
            log.debug("User found in Redis cache: {}", username);
            return cachedUser;
        }

        // If not in cache, try to get from database
        var existingUser = userRepository.findByUsername(username);
        if (existingUser.isPresent()) {
            log.debug("User found in database: {}", username);
            // Decrypt before returning and caching
            UserDto userDto = piiCryptoService.buildDecryptedUserDto(existingUser.get());
            redisInfrasService.setObjectWithTTL(USER_CACHE_KEY_PREFIX + username, userDto, USER_CACHE_TTL);
            return userDto;
        }

        // If user doesn't exist, create new user from token information
        log.debug("User not found, creating new user from token: {}", username);
        return createUserFromToken(username);
    }

    private UserDto createUserFromToken(String username) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            throw new AuthenticationException("Invalid token");
        }

        Jwt jwt = (Jwt) authentication.getPrincipal();

        // Extract user information from JWT token
        String email = jwt.getClaimAsString("email");
        String fullName = jwt.getClaimAsString("name");
        String givenName = jwt.getClaimAsString("given_name");
        String familyName = jwt.getClaimAsString("family_name");

        // Use name if available, otherwise combine given_name and family_name
        if (ObjectUtils.isEmpty(fullName) && !ObjectUtils.isEmpty(givenName)) {
            fullName = givenName;
            if (!ObjectUtils.isEmpty(familyName)) {
                fullName += " " + familyName;
            }
        }

        // Create new user entity
        User newUser = new User();
        newUser.setUsername(username);
        newUser.setEmail(null);
        newUser.setFullName(null);
        newUser.setPhone(null);
        newUser.setAddress(null);

        userRepository.saveAndFlush(newUser);

        // Encrypt PII after ID exists
        encryptAndSetUserFields(newUser, email, fullName, "", "");

        UserDto userDto = piiCryptoService.buildDecryptedUserDto(newUser);
        redisInfrasService.setObjectWithTTL(USER_CACHE_KEY_PREFIX + username, userDto, USER_CACHE_TTL);

        log.info("Created new user from token: {}", username);
        return userDto;
    }

    @Override
    @Transactional
    public UserDto updateBankToken(Long id, String bankToken) {
        log.info("Updating bank token for user ID: {}", id);

        // Find user by ID
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User ", " id: ", id.toString()));

        // Encrypt the bank token using KeyManagementService
        String encryptedToken = null;
        try {
            log.info("Encrypting bank token for user ID: {}", id);
            KeyEncryptRequest encryptRequest = KeyEncryptRequest.builder()
                    .userId(id.toString())
                    .data(bankToken)
                    .build();
            
            KeyEncryptResponse encryptResponse = keyManagementServiceClient.encrypt(encryptRequest);
            
            if (encryptResponse.isSuccess()) {
                encryptedToken = encryptResponse.getEncryptedData();
                log.info("Bank token encrypted successfully for user ID: {}", id);
            } else {
                log.error("Failed to encrypt bank token for user ID: {} - {}", id, encryptResponse.getMessage());
                throw new RuntimeException("Failed to encrypt bank token: " + encryptResponse.getMessage());
            }
        } catch (Exception e) {
            log.error("Error encrypting bank token for user ID: {}", id, e);
            throw new RuntimeException("Error encrypting bank token: " + e.getMessage());
        }

        // Update bank token with encrypted value and hash
        user.setBankToken(encryptedToken);
        user.setBankTokenHash(sha256(bankToken));
        userRepository.save(user);

        // Clear cache for this user
        redisInfrasService.delete(USER_CACHE_KEY_PREFIX + user.getUsername());


        log.info("Bank token updated and saved successfully for user ID: {}", id);
        return piiCryptoService.buildDecryptedUserDto(user);
    }

    @Override
    public Page<UserContactDto> searchByContact(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        if (ObjectUtils.isEmpty(keyword)) {
            return Page.empty(pageable);
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return Page.empty(pageable);
        }

        Map<Long, UserContactDto> collected = new LinkedHashMap<>();
        String hash = sha256(trimmed);

        userRepository.findByPhoneHash(hash).ifPresent(user -> collected.put(user.getId(), toContactDto(user)));
        userRepository.findByEmailHash(hash).ifPresent(user -> collected.put(user.getId(), toContactDto(user)));

        if (collected.isEmpty()) {
            return Page.empty(pageable);
        }
        List<UserContactDto> results = new ArrayList<>(collected.values());
        return new PageImpl<>(results, pageable, results.size());
    }

    private void encryptAndSetUserFields(User user, String plainEmail, String plainFullName, String plainPhone, String plainAddress) {
        try {
            // Ensure per-user AES key exists before running parallel encrypts
            ensureUserKeyInitialized(user.getId());
            java.util.concurrent.Executor executor = java.util.concurrent.Executors.newFixedThreadPool(4);
            var emailFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> encrypt(user.getId(), plainEmail), executor);
            var fullNameFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> encrypt(user.getId(), plainFullName), executor);
            var phoneFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> encrypt(user.getId(), plainPhone), executor);
            var addressFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> encrypt(user.getId(), plainAddress), executor);

            java.util.concurrent.CompletableFuture.allOf(emailFuture, fullNameFuture, phoneFuture, addressFuture).join();

            String encEmail = emailFuture.get();
            String encFullName = fullNameFuture.get();
            String encPhone = phoneFuture.get();
            String encAddress = addressFuture.get();

            if (encEmail != null) user.setEmail(encEmail);
            if (encFullName != null) user.setFullName(encFullName);
            if (encPhone != null) {
                user.setPhone(encPhone);
                user.setPhoneHash(sha256(nullToEmpty(plainPhone)));
            }
            if (encAddress != null) user.setAddress(encAddress);

            // Set hashes for all available plaintexts
            if (!ObjectUtils.isEmpty(plainEmail)) {
                user.setEmailHash(sha256(plainEmail));
            }
            if (!ObjectUtils.isEmpty(plainFullName)) {
                user.setFullNameHash(sha256(plainFullName));
            }
            if (!ObjectUtils.isEmpty(plainAddress)) {
                user.setAddressHash(sha256(plainAddress));
            }

            userRepository.save(user);
            if (executor instanceof java.util.concurrent.ExecutorService es) {
                es.shutdown();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to encrypt user fields", e);
        }
    }

    private void ensureUserKeyInitialized(Long userId) {
        try {
            var req = com.finance.userservice.dto.KeyGenerateRequest.builder().userId(String.valueOf(userId)).build();
            keyManagementServiceClient.generate(req);
        } catch (Exception ignored) {
            // If key already exists, the generate endpoint may return 400; ignore to allow encryption to proceed
        }
    }

    private String encrypt(Long userId, String plain) {
        if (ObjectUtils.isEmpty(plain)) return plain;
        KeyEncryptRequest request = KeyEncryptRequest.builder()
                .userId(userId.toString())
                .data(plain)
                .build();
        KeyEncryptResponse response = keyManagementServiceClient.encrypt(request);
        if (response != null && response.isSuccess()) {
            return response.getEncryptedData();
        }
        throw new RuntimeException("Encrypt failed" + (response != null ? (": " + response.getMessage()) : ""));
    }

    private String decrypt(Long userId, String encrypted) {
        if (ObjectUtils.isEmpty(encrypted)) return encrypted;
        KeyDecryptRequest request = KeyDecryptRequest.builder()
                .userId(userId.toString())
                .encryptedData(encrypted)
                .build();
        KeyDecryptResponse response = keyManagementServiceClient.decrypt(request);
        if (response != null && response.isSuccess()) {
            return response.getDecryptedData();
        }
        throw new RuntimeException("Decrypt failed" + (response != null ? (": " + response.getMessage()) : ""));
    }

    

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private UserContactDto toContactDto(User user) {
        UserDto dto = piiCryptoService.buildDecryptedUserDto(user);
        return UserContactDto.builder()
                .id(dto.getId())
                .username(dto.getUsername())
                .fullName(dto.getFullName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .build();
    }

}
