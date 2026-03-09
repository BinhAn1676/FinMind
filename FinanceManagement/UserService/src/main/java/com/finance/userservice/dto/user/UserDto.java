package com.finance.userservice.dto.user;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UserDto {

    private Long id;
    @NotEmpty(message = "Username can not be a null or empty")
    private String username;
    @NotEmpty(message = "Email can not be a null or empty")
    private String email;
    @Pattern(regexp="(^$|[0-9]{10})",message = "Phone Number must be 10 digits")
    private String phone;
    @NotEmpty(message = "Full Name can not be a null or empty")
    private String fullName;
    private String address;
    private LocalDate birthDay;
    private String bankToken;
    private AvatarFile avatarFile;
    
    // Profile fields
    private String avatar; // File key for avatar
    private String bio;
    private String dateOfBirth; // String format for frontend compatibility

    @Data
    public static class AvatarFile {
        private String id;
        private String fileName;
        private String fileType;
        private String fileCode;
        private String url;
        private String liveUrl;
    }
}
