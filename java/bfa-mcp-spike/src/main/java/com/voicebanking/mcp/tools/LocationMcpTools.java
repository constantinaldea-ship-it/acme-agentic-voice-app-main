package com.voicebanking.mcp.tools;

import com.voicebanking.mcp.location.BranchDto;
import com.voicebanking.mcp.location.BranchSearchRequest;
import com.voicebanking.mcp.location.BranchSearchResponse;
import com.voicebanking.mcp.location.LocationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * MCP tool definitions for location services.
 *
 * <p>Exposes {@link LocationService} operations as MCP tools via Spring AI
 * {@code @Tool} annotations. The boot starter auto-registers these as MCP tools
 * served via Streamable HTTP at {@code /mcp}.</p>
 *
 * <h3>Tools exposed:</h3>
 * <ul>
 *   <li>{@code branch_search} — Search branches with filters (city, address, GPS, brand)</li>
 *   <li>{@code branch_details} — Get full details for a single branch by ID</li>
 * </ul>
 *
 * <h3>Spike context:</h3>
 * <p>This class is part of the ADR-CES-003 spike (feature branch only).
 * It validates service-layer reuse and measures MCP vs REST latency.</p>
 *
 * @author Augment Agent
 * @since 2026-02-09
 */
@Component
public class LocationMcpTools {

    private static final Logger log = LoggerFactory.getLogger(LocationMcpTools.class);

    private final LocationService locationService;

    public LocationMcpTools(LocationService locationService) {
        this.locationService = locationService;
    }

    @Tool(name = "branch_search", description = """
            Search for Acme Bank branches by city, address, postal code, GPS coordinates, or brand.
            All parameters are optional and can be combined.
            Results are always distance-sorted from a reference point.
            Supports Deutsche Bank and Postbank branches across Germany.
            Data sourced from the Deutsche Bank Filialfinder API (238 branches, 50 cities).""")
    public BranchSearchResponse branchSearch(
            @ToolParam(description = "City name (case-insensitive, partial match). E.g., 'Köln', 'Frankfurt'.", required = false) String city,
            @ToolParam(description = "Street name or landmark (partial match). E.g., 'Alexanderplatz'.", required = false) String address,
            @ToolParam(description = "Postal code prefix. E.g., '50667' or '50' for all 50xxx.", required = false) String postalCode,
            @ToolParam(description = "Caller's latitude for distance-based sorting.", required = false) Double latitude,
            @ToolParam(description = "Caller's longitude for distance-based sorting.", required = false) Double longitude,
            @ToolParam(description = "Search radius in km (only with lat/lon). Default: 50.", required = false) Double radiusKm,
            @ToolParam(description = "Bank brand filter: 'Deutsche Bank' or 'Postbank'.", required = false) String brand,
            @ToolParam(description = "If true, only wheelchair-accessible branches.", required = false) Boolean accessible,
            @ToolParam(description = "Max results to return (default: 10, max: 50).", required = false) Integer limit) {

        long startNanos = System.nanoTime();

        log.info("[MCP] branch_search called: city={}, address={}, postalCode={}, lat={}, lon={}, brand={}, limit={}",
                city, address, postalCode, latitude, longitude, brand, limit);

        BranchSearchRequest request = new BranchSearchRequest(
                city, address, postalCode,
                latitude, longitude, radiusKm,
                brand, accessible, limit
        );

        BranchSearchResponse response = locationService.search(request);

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("[MCP] branch_search completed: {} results in {}ms", response.count(), durationMs);

        return response;
    }

    @Tool(name = "branch_details", description = """
            Get full details for a specific Acme Bank branch by its unique identifier.
            Returns address, opening hours, phone, services, accessibility, and transit info.
            Use branch IDs from branch_search results.""")
    public BranchDto branchDetails(
            @ToolParam(description = "Unique branch identifier from branch_search results.", required = true) String branchId) {

        long startNanos = System.nanoTime();

        log.info("[MCP] branch_details called: branchId={}", branchId);

        BranchDto result = locationService.getBranch(branchId);

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("[MCP] branch_details completed: branch='{}' in {}ms", result.name(), durationMs);

        return result;
    }
}
