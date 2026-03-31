package com.voicebanking.bfa.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Inbound request to invoke a tool through the BFA Gateway.
 *
 * <p>The gateway uses {@code toolName} to resolve the correct
 * {@link com.voicebanking.bfa.gateway.adapter.DomainAdapter} via the
 * {@link com.voicebanking.bfa.gateway.adapter.AdapterRegistry}.</p>
 *
 * @author Copilot
 * @since 2026-01-17
 */
@Schema(description = "Request to invoke a domain tool through the BFA Gateway")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolInvokeRequest(

        @Schema(description = "Tool identifier (maps to a registered DomainAdapter)",
                example = "branch-finder")
        @NotBlank(message = "toolName is required")
        String toolName,

        @Schema(description = "Free-form parameters forwarded to the adapter")
        @NotNull(message = "parameters must not be null")
        Map<String, Object> parameters
) {}
