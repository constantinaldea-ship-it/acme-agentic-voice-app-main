package com.voicebanking.bfa.adapter.branchfinder.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Canonical adapter invocation request.
 *
 * <p>This is the contract between the BFA Gateway and any domain adapter.
 * The gateway POSTs this to the adapter's {@code /actions/{action}} endpoint.</p>
 *
 * @author Copilot
 * @since 2026-03-01
 */
@Schema(description = "Adapter invocation request forwarded by the BFA Gateway")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AdapterRequest(

        @Schema(description = "Correlation ID for end-to-end tracing")
        String correlationId,

        @Schema(description = "Adapter-specific parameters")
        Map<String, Object> parameters
) {}
