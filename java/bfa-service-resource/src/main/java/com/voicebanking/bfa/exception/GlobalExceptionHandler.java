package com.voicebanking.bfa.exception;

import com.voicebanking.bfa.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST controllers.
 * 
 * <p>Converts exceptions to standardized ApiResponse format.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-02
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    
    @ExceptionHandler(BfaException.class)
    public ResponseEntity<ApiResponse<Void>> handleBfaException(BfaException ex, HttpServletRequest request) {
        String correlationId = MDC.get("correlationId");
        log.warn("[{}] BFA exception: {} - {}", correlationId, ex.getErrorCode(), ex.getMessage());
        
        Map<String, Object> details = new HashMap<>();
        if (ex instanceof ConsentRequiredException cre) {
            details.put("requiredConsents", cre.getRequiredConsents());
            details.put("missingConsents", cre.getMissingConsents());
        } else if (ex instanceof LegitimationDeniedException lde) {
            details.put("resourceType", lde.getResourceType());
            details.put("resourceId", lde.getResourceId());
            details.put("scope", lde.getScope());
        } else if (ex instanceof ResourceNotFoundException rnf) {
            details.put("resourceType", rnf.getResourceType());
            details.put("resourceId", rnf.getResourceId());
        }
        
        ApiResponse<Void> response = details.isEmpty()
            ? ApiResponse.error(ex.getErrorCode(), ex.getMessage(), correlationId)
            : ApiResponse.error(ex.getErrorCode(), ex.getMessage(), details, correlationId);
            
        return ResponseEntity.status(ex.getHttpStatus()).body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        String correlationId = MDC.get("correlationId");
        
        Map<String, Object> details = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                (a, b) -> a + "; " + b
            ));
        
        ApiResponse<Void> response = ApiResponse.error(
            "VALIDATION_FAILED",
            "Request validation failed",
            details,
            correlationId
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex, HttpServletRequest request) {
        String correlationId = MDC.get("correlationId");
        log.error("[{}] Unexpected error: {}", correlationId, ex.getMessage(), ex);
        
        ApiResponse<Void> response = ApiResponse.error(
            "INTERNAL_ERROR",
            "An unexpected error occurred",
            correlationId
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}
