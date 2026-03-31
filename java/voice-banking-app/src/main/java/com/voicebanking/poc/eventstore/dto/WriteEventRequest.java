package com.voicebanking.poc.eventstore.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound request DTO for writing events.
 * Kept separate from the JPA entity.
 */
public record WriteEventRequest(
        @NotBlank(message = "eventType is required")
        String eventType,

        @NotBlank(message = "payload is required")
        String payload,

        String metadata
) {}
