package com.finance.userservice.constant;

import java.util.Map;

public class UserConstants {

    public static final String  STATUS_201 = "201";
    public static final String  MESSAGE_201 = "User created successfully";
    public static final String  STATUS_200 = "200";
    public static final String  MESSAGE_200 = "Request processed successfully";
    public static final String  STATUS_417 = "417";
    public static final String  MESSAGE_417_UPDATE= "Update operation failed. Please try again or contact Dev team";
    public static final String  MESSAGE_417_DELETE= "Delete operation failed. Please try again or contact Dev team";
    public static class FileStatus {
        public static final String  STATUS_400 = "400";
        public static final String  MESSAGE_400_GET= "Cant get the current file";
    }

    public static class MessageNotification {
        public static final String  GROUP_INVITATION = "Group Invitation";
        public static final String  CHAT_ROOM_INVITATION = "Chat Room Invitation";
    }
    public static class Redis{
        // Redis key prefix for users
        public static final String USER_CACHE_KEY_PREFIX = "user:username:";
        public static final String USER_LOCK_KEY_PREFIX = "user_lock:";
        // Cache TTL in seconds (e.g., 1 hour)
        public static final long USER_CACHE_TTL = 3600;
    }
}
