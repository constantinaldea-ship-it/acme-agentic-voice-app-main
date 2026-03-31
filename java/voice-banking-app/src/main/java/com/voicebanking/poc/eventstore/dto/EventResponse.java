package com.voicebanking.poc.eventstore.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Outbound response DTO for events.
 */
public record EventResponse(
        UUID id,
        String eventType,
        String payload,
        Instant timestamp,
        String metadata
) {}
