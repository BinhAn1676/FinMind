package com.finance.userservice.service.crypto;

import com.finance.userservice.dto.KeyDecryptRequest;
import com.finance.userservice.dto.KeyDecryptResponse;
import com.finance.userservice.dto.user.UserDto;
import com.finance.userservice.entity.User;
import com.finance.userservice.mapper.UserMapper;
import com.finance.userservice.service.client.KeyManagementServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

@Service
@RequiredArgsConstructor
@Slf4j
public class PiiCryptoServiceImpl implements PiiCryptoService {

    private final KeyManagementServiceClient keyManagementServiceClient;

    @Override
    public UserDto buildDecryptedUserDto(User user) {
        var dto = UserMapper.mapToDtoUserInfo(user);
        try {
            java.util.concurrent.Executor executor = java.util.concurrent.Executors.newFixedThreadPool(4);
            var emailFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> safeDecrypt(user.getId(), user.getEmail()), executor);
            var fullNameFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> safeDecrypt(user.getId(), user.getFullName()), executor);
            var phoneFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> safeDecrypt(user.getId(), user.getPhone()), executor);
            var addressFuture = java.util.concurrent.CompletableFuture.supplyAsync(() -> safeDecrypt(user.getId(), user.getAddress()), executor);

            java.util.concurrent.CompletableFuture.allOf(emailFuture, fullNameFuture, phoneFuture, addressFuture).join();

            dto.setEmail(emailFuture.get());
            dto.setFullName(fullNameFuture.get());
            dto.setPhone(phoneFuture.get());
            dto.setAddress(addressFuture.get());
            if (executor instanceof java.util.concurrent.ExecutorService es) {
                es.shutdown();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return dto;
    }

    @Override
    public String decrypt(Long userId, String encrypted) {
        if (ObjectUtils.isEmpty(encrypted)) return encrypted;
        KeyDecryptRequest request = KeyDecryptRequest.builder()
                .userId(String.valueOf(userId))
                .encryptedData(encrypted)
                .build();
        KeyDecryptResponse response = keyManagementServiceClient.decrypt(request);
        if (response != null && response.isSuccess()) {
            return response.getDecryptedData();
        }
        throw new RuntimeException("Decrypt failed" + (response != null ? (": " + response.getMessage()) : ""));
    }

    private String safeDecrypt(Long userId, String encrypted) {
        try {
            return decrypt(userId, encrypted);
        } catch (Exception e) {
            return encrypted;
        }
    }
}


