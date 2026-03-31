package com.voicebanking.mcp.location;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Branch response DTO for MCP tool responses.
 *
 * <p>Includes all public branch information plus optional distance.
 * Copied from {@code bfa-service-resource} for spike isolation (without OpenAPI annotations).</p>
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BranchDto(
    String branchId,
    String name,
    String brand,
    String address,
    String city,
    String postalCode,
    double latitude,
    double longitude,
    String phone,
    String openingHours,
    boolean wheelchairAccessible,
    List<String> selfServices,
    List<String> branchServices,
    String transitInfo,
    String parkingInfo,
    Double distanceKm
) {

    /** Create a BranchDto from a domain Branch with no distance information. */
    public static BranchDto from(Branch branch) {
        return from(branch, null);
    }

    /** Create a BranchDto from a domain Branch with calculated distance. */
    public static BranchDto from(Branch branch, Double distanceKm) {
        return new BranchDto(
            branch.branchId(),
            branch.name(),
            branch.brand(),
            branch.address(),
            branch.city(),
            branch.postalCode(),
            branch.latitude(),
            branch.longitude(),
            branch.phone(),
            branch.openingHours(),
            branch.wheelchairAccessible(),
            nullIfEmpty(branch.selfServices()),
            nullIfEmpty(branch.branchServices()),
            branch.transitInfo(),
            branch.parkingInfo(),
            distanceKm != null ? Math.round(distanceKm * 100.0) / 100.0 : null
        );
    }

    private static List<String> nullIfEmpty(List<String> list) {
        return (list == null || list.isEmpty()) ? null : list;
    }
}
