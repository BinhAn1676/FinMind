package com.finance.notificationservice.model.file.response;

import com.finance.notificationservice.constants.FileCode;
import lombok.Data;

@Data
public class FileChatRoomResponseDto {
    private String id;
    private String fileName;
    private FileCode fileCode;
    private String fileType;
    private String url;
    private Long chatRoomId; // The ID of the chat room associated with the file
    private Long senderId; // ID of the user who sent the file
}
