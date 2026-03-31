package com.voicebanking.bfa.adapter.branchfinder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.Map;

/**
 * Canonical adapter invocation response.
 *
 * <p>Returned by every domain adapter's {@code /actions/{action}} endpoint.  The BFA
 * Gateway wraps this into its own {@code ToolInvokeResponse} before sending
 * it back to CES.</p>
 *
 * @author Copilot
 * @since 2026-03-01
 */
@Schema(description = "Adapter invocation response returned to the BFA Gateway")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdapterResponse(

        @Schema(description = "Whether the invocation succeeded")
        boolean success,

        @Schema(description = "Result payload (null on error)")
        Map<String, Object> data,

        @Schema(description = "Error code (null on success)")
        String errorCode,

        @Schema(description = "Error message (null on success)")
        String errorMessage,

        @Schema(description = "Correlation ID echoed back")
        String correlationId,

        @Schema(description = "Response timestamp")
        Instant timestamp
) {

    public static AdapterResponse success(Map<String, Object> data, String correlationId) {
        return new AdapterResponse(true, data, null, null, correlationId, Instant.now());
    }

    public static AdapterResponse error(String code, String message, String correlationId) {
        return new AdapterResponse(false, null, code, message, correlationId, Instant.now());
    }
}
