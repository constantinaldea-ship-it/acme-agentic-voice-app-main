package com.voicebanking.bfa.exception;

import org.springframework.http.HttpStatus;

/**
 * Base exception for BFA service errors.
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
public class BfaException extends RuntimeException {
    
    private final String errorCode;
    private final HttpStatus httpStatus;
    
    public BfaException(String errorCode, String message, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    public BfaException(String errorCode, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}
