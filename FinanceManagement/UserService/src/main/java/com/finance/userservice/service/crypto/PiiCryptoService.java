package com.finance.userservice.service.crypto;

import com.finance.userservice.dto.user.UserDto;
import com.finance.userservice.entity.User;

public interface PiiCryptoService {
    UserDto buildDecryptedUserDto(User user);
    String decrypt(Long userId, String encrypted);
}


