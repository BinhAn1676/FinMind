package com.finance.userservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
@ResponseStatus(value = HttpStatus.BAD_REQUEST)

public class NotAdminRoleException extends RuntimeException {

    public NotAdminRoleException(String msg){
        super(msg);
    }
}
