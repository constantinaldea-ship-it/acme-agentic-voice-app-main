package com.voicebanking.mcp.location;

/**
 * Query parameters for branch search.
 *
 * <p>All parameters are optional and can be combined. When {@code latitude}/{@code longitude}
 * are provided, results are distance-sorted from those coordinates.</p>
 *
 * <p>Copied from {@code bfa-service-resource} for spike isolation (without OpenAPI annotations).</p>
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
public record BranchSearchRequest(
    String city,
    String address,
    String postalCode,
    Double latitude,
    Double longitude,
    Double radiusKm,
    String brand,
    Boolean accessible,
    Integer limit
) {

    /** Return effective limit, clamped between 1 and 50, default 10. */
    public int effectiveLimit() {
        if (limit == null || limit <= 0) return 10;
        return Math.min(limit, 50);
    }

    /** Return effective search radius in km, default 50. */
    public double effectiveRadiusKm() {
        if (radiusKm == null || radiusKm <= 0) return 50.0;
        return radiusKm;
    }

    /**
     * Whether explicit GPS coordinates were provided.
     * Treats (0.0, 0.0) as "not provided" since some clients send zero for omitted numerics.
     */
    public boolean hasCoordinates() {
        return latitude != null && longitude != null
                && !(latitude == 0.0 && longitude == 0.0);
    }
}
