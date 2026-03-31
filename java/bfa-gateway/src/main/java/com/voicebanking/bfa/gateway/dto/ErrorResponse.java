package com.voicebanking.bfa.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Structured error payload embedded in {@link ToolInvokeResponse}.
 *
 * @author Copilot
 * @since 2026-01-17
 */
@Schema(description = "Error details for a failed tool invocation")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(

        @Schema(description = "Machine-readable error code", example = "ADAPTER_NOT_FOUND")
        String code,

        @Schema(description = "Human-readable message", example = "No adapter registered for tool 'unknown-tool'")
        String message,

        @Schema(description = "Additional error context")
        Map<String, Object> details
) {

    /** Convenience factory without details. */
    public static ErrorResponse of(String code, String message) {
        return new ErrorResponse(code, message, null);
    }

    /** Convenience factory with details. */
    public static ErrorResponse of(String code, String message, Map<String, Object> details) {
        return new ErrorResponse(code, message, details);
    }
}
