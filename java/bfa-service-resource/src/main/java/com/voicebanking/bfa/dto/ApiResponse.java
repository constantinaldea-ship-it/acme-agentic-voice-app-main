package com.voicebanking.bfa.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Standard API Response wrapper.
 * 
 * <p>All API responses are wrapped in this structure for consistency.</p>
 * 
 * @param <T> The type of the response data
 * @author Augment Agent
 * @since 2026-02-02
 */
@Schema(description = "Standard API response wrapper")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    
    @Schema(description = "Whether the request was successful")
    boolean success,
    
    @Schema(description = "Response data (null if error)")
    T data,
    
    @Schema(description = "Error details (null if success)")
    ErrorInfo error,
    
    @Schema(description = "Response metadata")
    ResponseMeta meta
) {
    /**
     * Error information structure.
     */
    public record ErrorInfo(
        @Schema(description = "Error code", example = "CONSENT_REQUIRED")
        String code,
        
        @Schema(description = "Human-readable error message")
        String message,
        
        @Schema(description = "Additional error details")
        Map<String, Object> details
    ) {}
    
    /**
     * Response metadata.
     */
    public record ResponseMeta(
        @Schema(description = "Correlation ID for tracing", example = "corr-12345")
        String correlationId,
        
        @Schema(description = "Response timestamp")
        Instant timestamp,
        
        @Schema(description = "API version", example = "1.0")
        String apiVersion,
        
        @Schema(description = "Pagination info (if applicable)")
        PaginationInfo pagination
    ) {}
    
    /**
     * Pagination information.
     */
    public record PaginationInfo(
        @Schema(description = "Current page (0-indexed)", example = "0")
        int page,
        
        @Schema(description = "Page size", example = "20")
        int size,
        
        @Schema(description = "Total elements", example = "150")
        long totalElements,
        
        @Schema(description = "Total pages", example = "8")
        int totalPages,
        
        @Schema(description = "Whether there are more pages")
        boolean hasNext,
        
        @Schema(description = "Whether this is the first page")
        boolean isFirst,
        
        @Schema(description = "Whether this is the last page")
        boolean isLast
    ) {}
    
    /**
     * Factory for successful response.
     */
    public static <T> ApiResponse<T> success(T data, String correlationId) {
        return new ApiResponse<>(
            true,
            data,
            null,
            new ResponseMeta(correlationId, Instant.now(), "1.0", null)
        );
    }
    
    /**
     * Factory for successful response with pagination.
     */
    public static <T> ApiResponse<T> success(T data, String correlationId, PaginationInfo pagination) {
        return new ApiResponse<>(
            true,
            data,
            null,
            new ResponseMeta(correlationId, Instant.now(), "1.0", pagination)
        );
    }
    
    /**
     * Factory for error response.
     */
    public static <T> ApiResponse<T> error(String code, String message, String correlationId) {
        return new ApiResponse<>(
            false,
            null,
            new ErrorInfo(code, message, null),
            new ResponseMeta(correlationId, Instant.now(), "1.0", null)
        );
    }
    
    /**
     * Factory for error response with details.
     */
    public static <T> ApiResponse<T> error(String code, String message, Map<String, Object> details, String correlationId) {
        return new ApiResponse<>(
            false,
            null,
            new ErrorInfo(code, message, details),
            new ResponseMeta(correlationId, Instant.now(), "1.0", null)
        );
    }
}
