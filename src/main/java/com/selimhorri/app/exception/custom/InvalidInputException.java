package com.selimhorri.app.exception.custom;

import com.selimhorri.app.exception.ErrorCode;

public class InvalidInputException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    public InvalidInputException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public InvalidInputException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public InvalidInputException(ErrorCode errorCode, Object... args) {
        super(errorCode.formatMessage(args));
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}