package com.selimhorri.app.exception.custom;

import com.selimhorri.app.exception.ErrorCode;

public class ResourceNotFoundException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    public ResourceNotFoundException(String message) {
        super(message);
        this.errorCode = ErrorCode.PAYMENT_NOT_FOUND;
    }
    
    public ResourceNotFoundException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public ResourceNotFoundException(ErrorCode errorCode, Object... args) {
        super(errorCode.formatMessage(args));
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}