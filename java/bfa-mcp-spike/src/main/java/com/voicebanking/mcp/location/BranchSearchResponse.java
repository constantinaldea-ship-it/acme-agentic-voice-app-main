package com.voicebanking.mcp.location;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Search response wrapper with branch results and search context.
 *
 * <p>Includes the reference point used for distance calculation so the LLM
 * can explain search context (e.g., "I searched from the centre of Köln").</p>
 *
 * <p>Copied from {@code bfa-service-resource} for spike isolation.</p>
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BranchSearchResponse(
    List<BranchDto> branches,
    int count,
    int totalMatches,
    ReferencePoint referencePoint
) {

    /** The geographic reference point and how it was determined. */
    public record ReferencePoint(
        double latitude,
        double longitude,
        String source
    ) {}
}
