package com.voicebanking.bfa.location;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * Branch response DTO for the Location Services API.
 *
 * <p>Includes all public branch information plus optional distance, services,
 * and accessibility fields. Backed by real Deutsche Bank Filialfinder data.</p>
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
@Schema(description = "Bank branch or service centre information")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BranchDto(

    @Schema(description = "Unique branch identifier", example = "20337740")
    String branchId,

    @Schema(description = "Descriptive branch name", example = "Deutsche Bank Wandsbeker Marktstra\u00dfe 1, Hamburg")
    String name,

    @Schema(description = "Bank brand",
            example = "Deutsche Bank",
            allowableValues = {"Deutsche Bank", "Postbank"})
    String brand,

    @Schema(description = "Street address with house number", example = "Wandsbeker Marktstra\u00dfe 1")
    String address,

    @Schema(description = "City", example = "Hamburg")
    String city,

    @Schema(description = "German postal code (PLZ)", example = "22041")
    String postalCode,

    @Schema(description = "GPS latitude", example = "53.57061")
    double latitude,

    @Schema(description = "GPS longitude", example = "10.06087")
    double longitude,

    @Schema(description = "Branch phone number (null if unavailable)",
            example = "+49 40 658000 0")
    String phone,

    @Schema(description = "Opening hours in German format. The LLM can parse this directly.",
            example = "Mo 10:00-12:30, 14:00-18:00; Di 10:00-12:30, 14:00-18:00")
    String openingHours,

    @Schema(description = "Whether the branch has wheelchair/barrier-free access")
    boolean wheelchairAccessible,

    @Schema(description = "Self-service terminal offerings",
            example = "[\"Bargeldauszahlung\", \"Kontoausz\u00fcge drucken\"]")
    List<String> selfServices,

    @Schema(description = "In-branch counter services",
            example = "[\"Wertschlie\u00dff\u00e4cher\", \"Fremde W\u00e4hrungen und Edelmetalle kaufen/verkaufen\"]")
    List<String> branchServices,

    @Schema(description = "Whether the branch offers advisory or consultation services",
            example = "true")
    boolean advisoryAvailable,

    @Schema(description = "Public transit directions (U-Bahn, S-Bahn, Bus)",
            example = "U- und S Bahn: Station Wandsbeker Chaussee mit U1 und S1")
    String transitInfo,

    @Schema(description = "Parking information",
            example = "Parkhaus im Geb\u00e4ude")
    String parkingInfo,

    @Schema(description = "Distance from search reference point in kilometres. "
            + "Null if no geographic reference point was used.",
            example = "2.34")
    Double distanceKm

) {

    /**
     * Create a BranchDto from a domain Branch with no distance information.
     */
    public static BranchDto from(Branch branch) {
        return from(branch, null);
    }

    /**
     * Create a BranchDto from a domain Branch with calculated distance.
     *
     * @param branch     the domain branch
     * @param distanceKm distance from reference point (nullable)
     */
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
                        branch.advisoryAvailable(),
            branch.transitInfo(),
            branch.parkingInfo(),
            distanceKm != null ? Math.round(distanceKm * 100.0) / 100.0 : null
        );
    }

    private static List<String> nullIfEmpty(List<String> list) {
        return (list == null || list.isEmpty()) ? null : list;
    }
}
