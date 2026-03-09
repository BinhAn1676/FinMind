package com.finance.userservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)

public class CustomException extends RuntimeException {

    public CustomException(String msg){
        super(msg);
    }
}
