package com.finance.notificationservice.model.file.request;

import com.finance.notificationservice.constants.FileCode;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class FileChatRoomRequestDto {
    private String id;
    private MultipartFile file;
    private Long chatRoomId;
    private FileCode fileCode;
    private Long senderId;
}
