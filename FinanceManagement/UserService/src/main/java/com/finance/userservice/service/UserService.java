package com.finance.userservice.service;

import com.finance.userservice.dto.user.UserContactDto;
import com.finance.userservice.dto.user.UserDto;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.data.domain.Page;

public interface UserService {
    UserDto create(UserDto user);

    UserDto getById(@NotEmpty(message = "Id can not be empty or null") Long id);

    UserDto update(Long id, UserDto user);

    boolean delete(@NotEmpty(message = "Id can not be empty or null") Long id);

    UserDto getByUsername(@NotEmpty(message = "Username can not be empty or null") String username);

    Page<UserDto> getUsers(String textSearch, int page, int size);

    UserDto getUserInfoFromToken();
    
    UserDto updateBankToken(Long id, String bankToken);

    Page<UserContactDto> searchByContact(String keyword, int page, int size);
}
