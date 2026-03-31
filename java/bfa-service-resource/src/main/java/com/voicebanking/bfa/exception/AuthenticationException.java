package com.voicebanking.bfa.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown for authentication failures.
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
public class AuthenticationException extends BfaException {
    
    public AuthenticationException(String message) {
        super("AUTHENTICATION_FAILED", message, HttpStatus.UNAUTHORIZED);
    }
    
    public AuthenticationException(String message, Throwable cause) {
        super("AUTHENTICATION_FAILED", message, HttpStatus.UNAUTHORIZED, cause);
    }
}
