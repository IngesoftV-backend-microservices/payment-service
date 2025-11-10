package com.selimhorri.app.exception;

import java.time.LocalDateTime;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.validation.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.selimhorri.app.exception.custom.DuplicateResourceException;
import com.selimhorri.app.exception.custom.ExternalServiceException;
import com.selimhorri.app.exception.custom.InvalidInputException;
import com.selimhorri.app.exception.custom.InvalidPaymentStatusException;
import com.selimhorri.app.exception.custom.ResourceNotFoundException;

import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class ApiExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(
            ResourceNotFoundException ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.warn("Resource not found - TraceId: {} - Path: {} - Message: {}", 
                traceId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND.value())
                .errorCode(ex.getErrorCode() != null ? 
                        ex.getErrorCode().getCode() : "ERR_3000")
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }
    
    @ExceptionHandler(DuplicateResourceException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateResourceException(
            DuplicateResourceException ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.warn("Duplicate resource - TraceId: {} - Path: {} - Message: {}", 
                traceId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .errorCode(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }
    
    @ExceptionHandler(InvalidPaymentStatusException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPaymentStatusException(
            InvalidPaymentStatusException ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.warn("Invalid payment status - TraceId: {} - Path: {} - Message: {}", 
                traceId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInputException(
            InvalidInputException ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.warn("Invalid input - TraceId: {} - Path: {} - Message: {}", 
                traceId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(ExternalServiceException.class)
    public ResponseEntity<ErrorResponse> handleExternalServiceException(
            ExternalServiceException ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.error("External service error - TraceId: {} - Path: {} - Message: {}", 
                traceId, request.getRequestURI(), ex.getMessage(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.SERVICE_UNAVAILABLE.value())
                .errorCode(ex.getErrorCode().getCode())
                .message(ex.getMessage())
                .details("Unable to communicate with external service")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.warn("Validation error - TraceId: {} - Path: {} - Errors: {}", 
                traceId, request.getRequestURI(), ex.getBindingResult().getErrorCount());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ErrorCode.VALIDATION_ERROR.getCode())
                .message("Validation failed for one or more fields")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errorResponse.addValidationError(error.getField(), error.getDefaultMessage());
        }
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.warn("Constraint violation - TraceId: {} - Path: {} - Message: {}", 
                traceId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ErrorCode.VALIDATION_ERROR.getCode())
                .message("Constraint violation")
                .details(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.warn("Malformed JSON - TraceId: {} - Path: {} - Message: {}", 
                traceId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ErrorCode.INVALID_INPUT.getCode())
                .message("Malformed JSON request")
                .details("Please check your request body format")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.warn("Type mismatch - TraceId: {} - Path: {} - Parameter: {} - Message: {}", 
                traceId, request.getRequestURI(), ex.getName(), ex.getMessage());
        
        String expectedType = ex.getRequiredType() != null ? 
                ex.getRequiredType().getSimpleName() : "unknown";
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ErrorCode.INVALID_INPUT.getCode())
                .message(String.format("Invalid value for parameter '%s'", ex.getName()))
                .details(String.format("Expected type: %s", expectedType))
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolationException(
            DataIntegrityViolationException ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.error("Database constraint violation - TraceId: {} - Path: {}", 
                traceId, request.getRequestURI(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT.value())
                .errorCode(ErrorCode.CONSTRAINT_VIOLATION.getCode())
                .message("Database constraint violation")
                .details("The operation violates a database constraint")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }
    
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalStateException(
            IllegalStateException ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.warn("Illegal state - TraceId: {} - Path: {} - Message: {}", 
                traceId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ErrorCode.INVALID_INPUT.getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.warn("Illegal argument - TraceId: {} - Path: {} - Message: {}", 
                traceId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ErrorCode.INVALID_INPUT.getCode())
                .message(ex.getMessage())
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(NumberFormatException.class)
    public ResponseEntity<ErrorResponse> handleNumberFormatException(
            NumberFormatException ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.warn("Invalid number format - TraceId: {} - Path: {} - Message: {}", 
                traceId, request.getRequestURI(), ex.getMessage());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .errorCode(ErrorCode.INVALID_INPUT.getCode())
                .message("Invalid ID format. Please provide a valid number")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception ex,
            HttpServletRequest request) {
        
        String traceId = generateTraceId();
        
        log.error("Unexpected error - TraceId: {} - Path: {} - Type: {}", 
                traceId, request.getRequestURI(), ex.getClass().getSimpleName(), ex);
        
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .errorCode(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
                .message("An unexpected error occurred")
                .details("Please contact support if the problem persists")
                .path(request.getRequestURI())
                .traceId(traceId)
                .build();
        
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    
    private String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}