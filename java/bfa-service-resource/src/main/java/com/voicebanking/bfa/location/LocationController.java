package com.voicebanking.bfa.location;

import com.voicebanking.bfa.annotation.Audited;
import com.voicebanking.bfa.annotation.RequiresConsent;
import com.voicebanking.bfa.dto.ApiResponse;
import com.voicebanking.bfa.filter.BfaSecurityFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for branch and ATM location search.
 *
 * <p>Provides resource-oriented endpoints for the Location Services Agent.
 * Branch data is public information — no resource-level legitimation required,
 * only AI interaction consent and audit logging.</p>
 *
 * <h3>Endpoints:</h3>
 * <ul>
 *   <li>{@code GET /api/v1/branches} — Search with optional filters</li>
 *   <li>{@code GET /api/v1/branches/{branchId}} — Get single branch details</li>
 * </ul>
 *
 * <h3>OpenAPI contract:</h3>
 * <p>Standalone spec at {@code /api-docs/location-services}</p>
 *
 * @author Augment Agent
 * @since 2026-02-07
 * @see LocationService
 * @see LocationOpenApiConfig
 */
@RestController
@RequestMapping("/api/v1/branches")
@Tag(name = "Location Services", description = "Branch and ATM location search for the Voice Banking Assistant")
public class LocationController {

    private static final Logger log = LoggerFactory.getLogger(LocationController.class);

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    /**
     * Search branches with flexible, combinable filters.
     *
     * <p>All parameters are optional. Results are always distance-sorted.
     * When no coordinates are provided, the city centroid is used as the
     * reference point for distance calculation.</p>
     */
    @GetMapping
    @RequiresConsent({"AI_INTERACTION"})
    @Audited(operation = "SEARCH_BRANCHES")
    @Operation(
            summary = "Search branches",
            description = """
                    Search for bank branches by city, address, postal code, GPS coordinates, or brand.
                    All filter parameters are optional and can be combined.
                    Supports multi-brand: Deutsche Bank and Postbank.
                    
                    Results are always distance-sorted from a reference point:
                    - If latitude/longitude provided → sort from those coordinates
                    - If only city provided → sort from city centroid (average of all branches in that city)
                    - If only postal code provided → sort from postal code centroid
                    - Otherwise → sort from the geographic centre of all branch data
                    
                    The reference point used is included in the response for transparency.
                    Data sourced from the Deutsche Bank Filialfinder API."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Search results (may be empty if no branches match)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Invalid parameters (e.g., latitude without longitude)"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "403",
                    description = "Missing AI_INTERACTION consent")
    })
    public ResponseEntity<ApiResponse<BranchSearchResponse>> searchBranches(
            @ParameterObject BranchSearchRequest request,
            HttpServletRequest httpRequest) {

        String correlationId = (String) httpRequest.getAttribute(BfaSecurityFilter.ATTR_CORRELATION_ID);

        // Validate: coordinates must come in pairs
        if ((request.latitude() != null) != (request.longitude() != null)) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error(
                            "INVALID_COORDINATES",
                            "Both latitude and longitude must be provided together",
                            correlationId)
            );
        }

        BranchSearchResponse result = locationService.search(request);

        log.debug("[{}] Branch search: {} results returned", correlationId, result.count());

        return ResponseEntity.ok(ApiResponse.success(result, correlationId));
    }

    /**
     * Get full details for a single branch by its unique identifier.
     *
     * <p>Returns all public branch information including address, opening hours,
     * phone number, and accessibility status.</p>
     */
    @GetMapping("/{branchId}")
    @RequiresConsent({"AI_INTERACTION"})
    @Audited(operation = "GET_BRANCH")
    @Operation(
            summary = "Get branch details",
            description = """
                    Returns full details for a specific branch by its unique identifier.
                    Includes address, opening hours, phone, and accessibility information.
                    
                    The opening hours string is in German format and can be parsed by the LLM
                    to answer questions like "Is it open on Saturday?" or "What time does it close?"."""
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Branch details retrieved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Branch not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "Authentication required")
    })
    public ResponseEntity<ApiResponse<BranchDto>> getBranch(
            @Parameter(description = "Unique branch identifier", example = "DB-DE-00003")
            @PathVariable String branchId,
            HttpServletRequest httpRequest) {

        String correlationId = (String) httpRequest.getAttribute(BfaSecurityFilter.ATTR_CORRELATION_ID);

        BranchDto branch = locationService.getBranch(branchId);

        return ResponseEntity.ok(ApiResponse.success(branch, correlationId));
    }
}
