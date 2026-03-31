package com.voicebanking.mcp.location;

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
 * fallback, and result ranking.</p>
 *
 * <p>Copied from {@code bfa-service-resource} for spike isolation.
 * Uses a simplified exception model (RuntimeException instead of BfaException).</p>
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
     * Results are always distance-sorted from a reference point.
     */
    public BranchSearchResponse search(BranchSearchRequest request) {
        log.debug("Branch search: city={}, address={}, postalCode={}, lat={}, lon={}, "
                + "brand={}, accessible={}, limit={}",
                request.city(), request.address(), request.postalCode(),
                request.latitude(), request.longitude(),
                request.brand(), request.accessible(), request.limit());

        List<Branch> filtered = applyFilters(request);
        ReferencePointResult refPoint = resolveReferencePoint(request);

        List<BranchWithDistance> ranked = filtered.stream()
                .map(b -> new BranchWithDistance(b,
                        haversine(refPoint.coordinate().latitude(), refPoint.coordinate().longitude(),
                                b.latitude(), b.longitude())))
                .sorted(Comparator.comparingDouble(BranchWithDistance::distanceKm))
                .toList();

        List<BranchWithDistance> radiusFiltered = ranked;
        if (request.hasCoordinates()) {
            double radius = request.effectiveRadiusKm();
            radiusFiltered = ranked.stream()
                    .filter(bd -> bd.distanceKm() <= radius)
                    .toList();
        }

        int totalMatches = radiusFiltered.size();

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
     * @throws RuntimeException if branch not found
     */
    public BranchDto getBranch(String branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new RuntimeException("Branch not found: " + branchId));
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

    private boolean addressMatches(Branch branch, String query) {
        String addr = branch.address().toLowerCase();
        String name = branch.name().toLowerCase();
        if (addr.contains(query) || name.contains(query)) {
            return true;
        }
        String queryBase = stripGermanStreetSuffix(query);
        if (queryBase.length() >= 5) {
            String addrBase = stripGermanStreetSuffix(addr);
            if (addrBase.startsWith(queryBase) || queryBase.startsWith(addrBase)) {
                return true;
            }
        }
        return false;
    }

    private String stripGermanStreetSuffix(String input) {
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
        if (request.hasCoordinates()) {
            return new ReferencePointResult(
                    new BranchRepository.Coordinate(request.latitude(), request.longitude()),
                    "PROVIDED_COORDINATES"
            );
        }
        if (request.city() != null && !request.city().isBlank()) {
            return branchRepository.getCityCentroid(request.city())
                    .map(c -> new ReferencePointResult(c, "CITY_CENTROID"))
                    .orElse(new ReferencePointResult(
                            branchRepository.getDataCentroid(), "DATA_CENTROID"));
        }
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
