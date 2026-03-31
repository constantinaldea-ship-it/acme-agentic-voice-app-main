package com.voicebanking.bfa.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Outbound response from a tool invocation.
 *
 * <p>Wraps the adapter payload with gateway metadata (correlationId,
 * timestamp, adapter identifier). The {@code data} map is adapter-specific
 * and may have been sanitised by the Response PEP filter before
 * serialisation.</p>
 *
 * @author Copilot
 * @since 2026-01-17
 */
@Schema(description = "Response from a domain tool invocation via the BFA Gateway")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolInvokeResponse(

        @Schema(description = "Whether the invocation succeeded")
        boolean success,

        @Schema(description = "Tool that produced this response", example = "branch-finder")
        String toolName,

        @Schema(description = "Adapter-specific result payload")
        Map<String, Object> data,

        @Schema(description = "Error details (null on success)")
        ErrorResponse error,

        @Schema(description = "Request correlation ID")
        String correlationId,

        @Schema(description = "Response timestamp")
        Instant timestamp
) {

    /** Factory — successful invocation. */
    public static ToolInvokeResponse success(String toolName,
                                              Map<String, Object> data,
                                              String correlationId) {
        return new ToolInvokeResponse(true, toolName, data, null,
                correlationId, Instant.now());
    }

    /** Factory — failed invocation. */
    public static ToolInvokeResponse failure(String toolName,
                                              ErrorResponse error,
                                              String correlationId) {
        return new ToolInvokeResponse(false, toolName, null, error,
                correlationId, Instant.now());
    }
}
