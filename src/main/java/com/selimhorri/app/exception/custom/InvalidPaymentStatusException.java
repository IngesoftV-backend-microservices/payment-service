package com.selimhorri.app.exception.custom;

import com.selimhorri.app.exception.ErrorCode;

public class InvalidPaymentStatusException extends RuntimeException {
    
    private final ErrorCode errorCode;
    
    public InvalidPaymentStatusException(String message) {
        super(message);
        this.errorCode = ErrorCode.INVALID_PAYMENT_STATUS;
    }
    
    public InvalidPaymentStatusException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
    
    public InvalidPaymentStatusException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}