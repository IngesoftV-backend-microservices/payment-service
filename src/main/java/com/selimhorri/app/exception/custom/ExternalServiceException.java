package com.selimhorri.app.exception.custom;

import com.selimhorri.app.exception.ErrorCode;

public class ExternalServiceException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    public ExternalServiceException(String message) {
        super(message);
        this.errorCode = ErrorCode.EXTERNAL_SERVICE_ERROR;
    }
    
    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = ErrorCode.EXTERNAL_SERVICE_ERROR;
    }
    
    public ExternalServiceException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}