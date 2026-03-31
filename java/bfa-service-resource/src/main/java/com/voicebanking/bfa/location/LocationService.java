package com.voicebanking.bfa.location;

import com.voicebanking.bfa.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Business logic for branch location search.
 *
 * <p>Handles filtering, distance calculation (Haversine formula), city centroid
 * fallback when no explicit coordinates are provided, and result ranking.</p>
 *
 * <p>The LLM is the semantic layer — this service only does structured data operations.
 * City name resolution, disambiguation ("which Alexanderplatz?"), and natural language
 * understanding happen in the LLM before it calls this service.</p>
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
@Service
public class LocationService {

    private static final Logger log = LoggerFactory.getLogger(LocationService.class);
    private static final double EARTH_RADIUS_KM = 6371.0;

    private final BranchRepository branchRepository;

    public LocationService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    /**
     * Search branches with flexible, combinable criteria.
     *
     * <p>Results are always distance-sorted. The reference point for distance
     * calculation is determined by priority:</p>
     * <ol>
     *   <li>Explicit coordinates (if provided by caller/device)</li>
     *   <li>City centroid (average lat/lon of all branches in that city)</li>
     *   <li>Postal code centroid (average of branches matching postal code)</li>
     *   <li>Data centroid (fallback — geographic centre of all branch data)</li>
     * </ol>
     *
     * @param request search criteria (all optional, combinable)
     * @return distance-sorted results with reference point context
     */
    public BranchSearchResponse search(BranchSearchRequest request) {
        log.debug("Branch search: city={}, address={}, postalCode={}, lat={}, lon={}, "
                + "brand={}, accessible={}, limit={}",
                request.city(), request.address(), request.postalCode(),
                request.latitude(), request.longitude(),
                request.brand(), request.accessible(), request.limit());

        // Step 1: Apply filters
        List<Branch> filtered = applyFilters(request);

        // Step 2: Determine reference point for distance sorting
        ReferencePointResult refPoint = resolveReferencePoint(request);

        // Step 3: Calculate distances and sort
        List<BranchWithDistance> ranked = filtered.stream()
                .map(b -> new BranchWithDistance(b,
                        haversine(refPoint.coordinate().latitude(), refPoint.coordinate().longitude(),
                                b.latitude(), b.longitude())))
                .sorted(Comparator.comparingDouble(BranchWithDistance::distanceKm))
                .toList();

        // Step 4: Apply radius filter (only when explicit coordinates were provided)
        List<BranchWithDistance> radiusFiltered = ranked;
        if (request.hasCoordinates()) {
            double radius = request.effectiveRadiusKm();
            radiusFiltered = ranked.stream()
                    .filter(bd -> bd.distanceKm() <= radius)
                    .toList();
        }

        int totalMatches = radiusFiltered.size();

        // Step 5: Apply limit and convert to DTOs
        List<BranchDto> results = radiusFiltered.stream()
                .limit(request.effectiveLimit())
                .map(bd -> BranchDto.from(bd.branch(), bd.distanceKm()))
                .toList();

        log.info("Branch search: {} of {} matches returned (ref: {} at [{}, {}])",
                results.size(), totalMatches, refPoint.source(),
                refPoint.coordinate().latitude(), refPoint.coordinate().longitude());

        return new BranchSearchResponse(
                results,
                results.size(),
                totalMatches,
                new BranchSearchResponse.ReferencePoint(
                        refPoint.coordinate().latitude(),
                        refPoint.coordinate().longitude(),
                        refPoint.source()
                )
        );
    }

    /**
     * Get a single branch by its unique identifier.
     *
     * @param branchId branch identifier (e.g., "DB-DE-00003")
     * @return branch details
     * @throws ResourceNotFoundException if branch not found
     */
    public BranchDto getBranch(String branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new ResourceNotFoundException("Branch", branchId));
        return BranchDto.from(branch);
    }

    // ==========================================
    // Filtering
    // ==========================================

    private List<Branch> applyFilters(BranchSearchRequest request) {
        Stream<Branch> stream = branchRepository.findAll().stream();

        if (request.city() != null && !request.city().isBlank()) {
            String lowerCity = request.city().toLowerCase();
            stream = stream.filter(b -> b.city().toLowerCase().contains(lowerCity));
        }

        if (request.address() != null && !request.address().isBlank()) {
            String lowerAddress = request.address().toLowerCase();
            stream = stream.filter(b -> addressMatches(b, lowerAddress));
        }

        if (request.postalCode() != null && !request.postalCode().isBlank()) {
            stream = stream.filter(b -> b.postalCode().startsWith(request.postalCode()));
        }

        if (request.brand() != null && !request.brand().isBlank()) {
            String lowerBrand = request.brand().toLowerCase();
            stream = stream.filter(b -> b.brand().toLowerCase().contains(lowerBrand));
        }

        if (Boolean.TRUE.equals(request.accessible())) {
            stream = stream.filter(Branch::wheelchairAccessible);
        }

        return stream.toList();
    }

    /**
     * Flexible address matching: exact substring, or prefix-based overlap.
     * <p>Handles landmark/area names like "Alexanderplatz" matching
     * "Alexanderstraße" by checking if either the query or the address
     * starts with the other (minimum 5-char overlap to avoid false positives).</p>
     */
    private boolean addressMatches(Branch branch, String query) {
        String addr = branch.address().toLowerCase();
        String name = branch.name().toLowerCase();
        // Exact substring match (original behavior)
        if (addr.contains(query) || name.contains(query)) {
            return true;
        }
        // Extract the base word from the query (strip common German suffixes)
        String queryBase = stripGermanStreetSuffix(query);
        if (queryBase.length() >= 5) {
            // Check if any word in the address starts with the query base
            String addrBase = stripGermanStreetSuffix(addr);
            if (addrBase.startsWith(queryBase) || queryBase.startsWith(addrBase)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Strip common German street/place suffixes to get the root name.
     * E.g., "alexanderplatz" → "alexander", "alexanderstraße" → "alexander"
     */
    private String stripGermanStreetSuffix(String input) {
        // Remove common suffixes — order matters (longest first)
        String[] suffixes = {
            "straße", "strasse", "str.", "platz", "allee", "damm", "weg",
            "ring", "gasse", "ufer", "brücke", "bruecke", "markt"
        };
        String lower = input.trim().toLowerCase();
        for (String suffix : suffixes) {
            if (lower.endsWith(suffix) && lower.length() > suffix.length()) {
                return lower.substring(0, lower.length() - suffix.length());
            }
        }
        return lower;
    }

    // ==========================================
    // Reference Point Resolution
    // ==========================================

    private ReferencePointResult resolveReferencePoint(BranchSearchRequest request) {
        // Priority 1: Explicit coordinates from caller
        if (request.hasCoordinates()) {
            return new ReferencePointResult(
                    new BranchRepository.Coordinate(request.latitude(), request.longitude()),
                    "PROVIDED_COORDINATES"
            );
        }

        // Priority 2: City centroid (derived from branch data)
        if (request.city() != null && !request.city().isBlank()) {
            return branchRepository.getCityCentroid(request.city())
                    .map(c -> new ReferencePointResult(c, "CITY_CENTROID"))
                    .orElse(new ReferencePointResult(
                            branchRepository.getDataCentroid(), "DATA_CENTROID"));
        }

        // Priority 3: Postal code centroid
        if (request.postalCode() != null && !request.postalCode().isBlank()) {
            List<Branch> matches = branchRepository.findByPostalCode(request.postalCode());
            if (!matches.isEmpty()) {
                double avgLat = matches.stream().mapToDouble(Branch::latitude).average().orElse(0);
                double avgLon = matches.stream().mapToDouble(Branch::longitude).average().orElse(0);
                return new ReferencePointResult(
                        new BranchRepository.Coordinate(avgLat, avgLon),
                        "POSTAL_CODE_CENTROID"
                );
            }
        }

        // Fallback: Data centroid (geographic centre of all branches)
        return new ReferencePointResult(branchRepository.getDataCentroid(), "DATA_CENTROID");
    }

    // ==========================================
    // Internal Records
    // ==========================================

    private record ReferencePointResult(BranchRepository.Coordinate coordinate, String source) {}
    private record BranchWithDistance(Branch branch, double distanceKm) {}

    // ==========================================
    // Haversine Distance Calculation
    // ==========================================

    /**
     * Calculate great-circle distance between two points using the Haversine formula.
     *
     * @return distance in kilometres
     */
    static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }
}
