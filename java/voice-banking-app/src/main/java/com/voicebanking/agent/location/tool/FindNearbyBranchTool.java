package com.voicebanking.agent.location.tool;

import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.FunctionTool;
import com.voicebanking.agent.location.domain.Branch;
import com.voicebanking.agent.location.service.BranchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.List;

/**
 * ADK Tool: Find Nearby Branches
 * 
 * <p>Finds bank branches and ATMs near a given location.</p>
 * 
 * <p>Note: ADK FunctionTool requires static methods, so we use a holder pattern
 * with a static service reference set at initialization.</p>
 */
@Configuration
@Profile("cloud")
public class FindNearbyBranchTool {

    private static final Logger log = LoggerFactory.getLogger(FindNearbyBranchTool.class);

    // Static reference for tool execution (set at startup)
    private static BranchService branchServiceRef;

    @Bean
    public FunctionTool findNearbyBranchFunctionTool(BranchService branchService) throws NoSuchMethodException {
        // Set static reference for tool execution
        branchServiceRef = branchService;

        // Create FunctionTool from static method
        return FunctionTool.create(
            FindNearbyBranchTool.class.getMethod(
                "findNearbyBranches",
                Double.class,
                Double.class,
                Double.class,
                Integer.class,
                String.class
            )
        );
    }

    /**
     * Static tool method for ADK FunctionTool.
     * Finds bank branches and ATMs near a location.
     */
    @Schema(
        name = "findNearbyBranches",
        description = "Find Acme Bank branches and ATMs near a location. " +
                      "Returns sorted list by distance with addresses, opening hours, " +
                      "and accessibility information."
    )
    public static List<Branch> findNearbyBranches(
            @Schema(description = "User's latitude coordinate (e.g., 50.1109 for Frankfurt, 48.1371 for Munich)")
            Double latitude,

            @Schema(description = "User's longitude coordinate (e.g., 8.6821 for Frankfurt, 11.5754 for Munich)")
            Double longitude,

            @Schema(description = "Search radius in kilometers. Defaults to 5 km if not specified.")
            Double radiusKm,

            @Schema(description = "Maximum number of results to return. Defaults to 5 if not specified.")
            Integer limit,

            @Schema(description = "Filter by location type: 'branch' for full-service branches, " +
                                  "'atm' for ATMs only, 'flagship' for main branches, " +
                                  "or 'all' for everything. Defaults to 'all'.")
            String type) {

        log.info("FindNearbyBranchTool executing: lat={}, lon={}, radius={}, limit={}, type={}",
            latitude, longitude, radiusKm, limit, type);

        // Apply defaults
        double radius = (radiusKm != null) ? radiusKm : 5.0;
        int maxResults = (limit != null) ? limit : 5;
        String filterType = (type != null) ? type : "all";

        // Validate coordinates
        if (latitude == null || longitude == null) {
            throw new IllegalArgumentException(
                "Location is required. Please provide your latitude and longitude coordinates, " +
                "or share your location."
            );
        }

        // Validate coordinate ranges
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90 degrees.");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180 degrees.");
        }

        List<Branch> results = branchServiceRef.findNearby(
            latitude,
            longitude,
            radius,
            maxResults,
            filterType
        );

        log.info("FindNearbyBranchTool completed: {} locations found within {} km",
            results.size(), radius);

        return results;
    }
}
