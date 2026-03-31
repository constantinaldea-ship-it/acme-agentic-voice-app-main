package com.voicebanking.bfa.gateway.exception;

import com.voicebanking.bfa.gateway.dto.ErrorResponse;
import com.voicebanking.bfa.gateway.dto.ToolInvokeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for the BFA Gateway.
 *
 * <p>Converts all unhandled exceptions to a consistent
 * {@link ToolInvokeResponse} error envelope so callers always get
 * predictable JSON, even on unexpected failures.</p>
 *
 * @author Copilot
 * @since 2026-01-17
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ToolInvokeResponse> handleValidation(MethodArgumentNotValidException ex) {
        String correlationId = MDC.get("correlationId");

        Map<String, Object> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid",
                        (a, b) -> a + "; " + b));

        log.warn("[{}] Validation failed: {}", correlationId, fieldErrors);

        return ResponseEntity.badRequest()
                .body(ToolInvokeResponse.failure(
                        null,
                        ErrorResponse.of("VALIDATION_FAILED",
                                "Request validation failed", fieldErrors),
                        correlationId));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ToolInvokeResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        String correlationId = MDC.get("correlationId");
        log.warn("[{}] Malformed request body: {}", correlationId, ex.getMessage());

        return ResponseEntity.badRequest()
                .body(ToolInvokeResponse.failure(
                        null,
                        ErrorResponse.of("MALFORMED_REQUEST",
                                "Request body is not valid JSON"),
                        correlationId));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ToolInvokeResponse> handleGeneric(Exception ex) {
        String correlationId = MDC.get("correlationId");
        log.error("[{}] Unhandled exception: {}", correlationId, ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ToolInvokeResponse.failure(
                        null,
                        ErrorResponse.of("INTERNAL_ERROR",
                                "An unexpected error occurred"),
                        correlationId));
    }
}
