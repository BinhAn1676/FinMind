package com.finance.userservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST)
public class ResourceAlreadyExistException extends RuntimeException {

    public ResourceAlreadyExistException(String resourceName, String fieldName, String fieldValue){
        super(String.format("%s already existed with the given input data %s : '%s'", resourceName, fieldName, fieldValue));
    }
}