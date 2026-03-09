package com.finance.fileservice.exception;

public class FileUploadException extends FileServiceException {
    
    public FileUploadException(String message) {
        super(message);
    }
    
    public FileUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
