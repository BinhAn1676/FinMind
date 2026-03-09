package com.finance.userservice.mapper;

import com.finance.userservice.constant.FileCode;
import com.finance.userservice.dto.user.UserDto;
import com.finance.userservice.entity.User;
import com.finance.userservice.common.utils.ObjectMapperUtils;
import lombok.experimental.UtilityClass;

import java.util.Optional;
@UtilityClass
public class UserMapper {

    public static User mapToNewUser(UserDto userDto) {
        var user = ObjectMapperUtils.map(userDto, User.class);
        return user;
    }
    public static User mapToExistingUser(UserDto userDto,User user) {
        user = ObjectMapperUtils.map(userDto, user);
        user.setBirthDay(userDto.getBirthDay());
        return user;
    }

    public static UserDto mapToDto(User user) {
        var result = ObjectMapperUtils.map(user, UserDto.class);
        return result;
    }

    public static UserDto mapToDtoUserInfo(User user) {
        var result = ObjectMapperUtils.map(user, UserDto.class);
        result.setBirthDay(user.getBirthDay());
        return result;
    }
}
