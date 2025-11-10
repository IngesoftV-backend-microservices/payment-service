package com.selimhorri.app.exception.custom;

import com.selimhorri.app.exception.ErrorCode;

public class DuplicateResourceException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    public DuplicateResourceException(String message) {
        super(message);
        this.errorCode = ErrorCode.DUPLICATE_RESOURCE;
    }
    
    public DuplicateResourceException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public DuplicateResourceException(ErrorCode errorCode, Object... args) {
        super(errorCode.formatMessage(args));
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}