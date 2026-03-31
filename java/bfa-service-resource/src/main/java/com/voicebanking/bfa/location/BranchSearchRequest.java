package com.voicebanking.bfa.location;

import io.swagger.v3.oas.annotations.Parameter;

/**
 * Query parameters for branch search.
 *
 * <p>All parameters are optional and can be combined. When {@code latitude}/{@code longitude}
 * are provided, results are distance-sorted from those coordinates. When only {@code city}
 * is provided, results are distance-sorted from the city centroid (average lat/lon of
 * all branches in that city).</p>
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
public record BranchSearchRequest(

    @Parameter(description = "City name (case-insensitive, partial match). "
            + "E.g., 'Frankfurt' matches 'Frankfurt am Main'.",
            example = "K\u00f6ln")
    String city,

    @Parameter(description = "Street name or landmark (partial match, also searches branch names). "
            + "E.g., 'Alexanderplatz' or 'Hauptstra\u00dfe'.",
            example = "Hauptstra\u00dfe")
    String address,

    @Parameter(description = "Postal code prefix (matches start of PLZ). "
            + "E.g., '50' matches all branches in the 50xxx area.",
            example = "50667")
    String postalCode,

    @Parameter(description = "Caller's latitude for distance-based sorting and radius filtering. "
            + "Must be provided together with longitude.",
            example = "50.9375")
    Double latitude,

    @Parameter(description = "Caller's longitude for distance-based sorting and radius filtering. "
            + "Must be provided together with latitude.",
            example = "6.9603")
    Double longitude,

    @Parameter(description = "Search radius in kilometres (only applies when lat/lon provided). "
            + "Default: 50 km.",
            example = "10.0")
    Double radiusKm,

    @Parameter(description = "Filter by bank brand. "
            + "Values: 'Deutsche Bank', 'Postbank'.",
            example = "Deutsche Bank")
    String brand,

    @Parameter(description = "If true, return only wheelchair-accessible branches.")
    Boolean accessible,

    @Parameter(description = "Maximum number of results to return (default: 10, max: 50).",
            example = "5")
    Integer limit

) {

    /**
     * Return effective limit, clamped between 1 and 50, default 10.
     */
    public int effectiveLimit() {
        if (limit == null || limit <= 0) return 10;
        return Math.min(limit, 50);
    }

    /**
     * Return effective search radius in km, default 50.
     */
    public double effectiveRadiusKm() {
        if (radiusKm == null || radiusKm <= 0) return 50.0;
        return radiusKm;
    }

    /**
     * Whether explicit GPS coordinates were provided.
     * Treats (0.0, 0.0) as "not provided" since some clients (e.g., CX Agent Studio)
     * send zero instead of null for omitted numeric parameters.
     */
    public boolean hasCoordinates() {
        return latitude != null && longitude != null
                && !(latitude == 0.0 && longitude == 0.0);
    }
}
