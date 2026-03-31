package com.voicebanking.bfa.location;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Search response wrapper with branch results and search context.
 *
 * <p>Includes the reference point used for distance calculation so the LLM
 * can explain search context (e.g., "I searched from the centre of Köln").</p>
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
@Schema(description = "Branch search results with distance-sorting context")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BranchSearchResponse(

    @Schema(description = "Matching branches, sorted by distance from the reference point")
    List<BranchDto> branches,

    @Schema(description = "Number of branches returned (after limit applied)", example = "3")
    int count,

    @Schema(description = "Total branches matching filters before limit was applied", example = "7")
    int totalMatches,

    @Schema(description = "Geographic reference point used for distance calculation")
    ReferencePoint referencePoint

) {

    /**
     * The geographic reference point and how it was determined.
     */
    @Schema(description = "Geographic reference point and its source")
    public record ReferencePoint(

        @Schema(description = "Reference point latitude", example = "50.9375")
        double latitude,

        @Schema(description = "Reference point longitude", example = "6.9603")
        double longitude,

        @Schema(description = "How the reference point was determined: "
                + "PROVIDED_COORDINATES (caller's GPS), "
                + "CITY_CENTROID (average of all branches in the searched city), "
                + "POSTAL_CODE_CENTROID (average of branches matching postal code), "
                + "DATA_CENTROID (fallback: centre of all branch data)",
                example = "CITY_CENTROID",
                allowableValues = {"PROVIDED_COORDINATES", "CITY_CENTROID",
                        "POSTAL_CODE_CENTROID", "DATA_CENTROID"})
        String source

    ) {}
}
