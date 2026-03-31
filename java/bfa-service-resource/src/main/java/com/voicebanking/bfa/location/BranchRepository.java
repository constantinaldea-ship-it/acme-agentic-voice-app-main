package com.voicebanking.bfa.location;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * In-memory repository for branch data, loaded from JSON at startup.
 *
 * <p>Provides query methods for city, address, postal code, brand, and ID-based lookup.
 * City centroids are precomputed at startup for distance sorting when no explicit
 * coordinates are provided by the caller.</p>
 *
 * <p>Data source: {@code classpath:data/branches.json} (238 real branches from the
 * Deutsche Bank Filialfinder API across 50 German cities, 2 brands)</p>
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
@Repository
public class BranchRepository {

    private static final Logger log = LoggerFactory.getLogger(BranchRepository.class);
    private static final String JSON_PATH = "data/branches.json";
    private static final List<String> ADVISORY_KEYWORDS = List.of("beratung", "berater", "advisor");

    private final ObjectMapper objectMapper;
    private List<Branch> branches = List.of();
    private Map<String, Coordinate> cityCentroids = Map.of();

    /**
     * Simple latitude/longitude coordinate pair.
     */
    public record Coordinate(double latitude, double longitude) {}

    public BranchRepository(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void init() {
        this.branches = loadFromJson();
        this.cityCentroids = computeCityCentroids();
        long brands = branches.stream().map(Branch::brand).distinct().count();
        log.info("BranchRepository initialized: {} branches across {} cities, {} brands",
                branches.size(), cityCentroids.size(), brands);
    }

    // ==========================================
    // Query Methods
    // ==========================================

    /**
     * Get all branches.
     */
    public List<Branch> findAll() {
        return branches;
    }

    /**
     * Find a branch by its unique ID.
     */
    public Optional<Branch> findById(String branchId) {
        return branches.stream()
                .filter(b -> b.branchId().equalsIgnoreCase(branchId))
                .findFirst();
    }

    /**
     * Find branches in a city (case-insensitive, partial match).
     * "Frankfurt" matches "Frankfurt am Main".
     */
    public List<Branch> findByCity(String city) {
        String lowerCity = city.toLowerCase();
        return branches.stream()
                .filter(b -> b.city().toLowerCase().contains(lowerCity))
                .toList();
    }

    /**
     * Find branches by street address (case-insensitive, partial match).
     */
    public List<Branch> findByAddress(String address) {
        String lowerAddress = address.toLowerCase();
        return branches.stream()
                .filter(b -> b.address().toLowerCase().contains(lowerAddress))
                .toList();
    }

    /**
     * Find branches by postal code prefix match.
     * "50" matches all branches in the 50xxx area.
     */
    public List<Branch> findByPostalCode(String postalCode) {
        return branches.stream()
                .filter(b -> b.postalCode().startsWith(postalCode))
                .toList();
    }

    /**
     * Get the precomputed centroid (average lat/lon) for a city.
     * Uses partial matching on city name.
     */
    public Optional<Coordinate> getCityCentroid(String city) {
        String lowerCity = city.toLowerCase();
        return cityCentroids.entrySet().stream()
                .filter(e -> e.getKey().toLowerCase().contains(lowerCity))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /**
     * Get the centroid of all branches (fallback when no city matches).
     */
    public Coordinate getDataCentroid() {
        double avgLat = branches.stream().mapToDouble(Branch::latitude).average().orElse(51.0);
        double avgLon = branches.stream().mapToDouble(Branch::longitude).average().orElse(10.0);
        return new Coordinate(avgLat, avgLon);
    }

    /**
     * Get total number of loaded branches.
     */
    public int count() {
        return branches.size();
    }

    // ==========================================
    // JSON Loading
    // ==========================================

    private List<Branch> loadFromJson() {
        try {
            ClassPathResource resource = new ClassPathResource(JSON_PATH);
            try (InputStream is = resource.getInputStream()) {
                List<BranchJson> rawList = objectMapper.readValue(is,
                        new TypeReference<List<BranchJson>>() {});

                List<Branch> loaded = rawList.stream()
                        .map(BranchJson::toDomain)
                        .toList();

                log.info("Loaded {} branches from {}", loaded.size(), JSON_PATH);
                return List.copyOf(loaded);
            }
        } catch (Exception e) {
            log.error("Failed to load branch data from {}: {}", JSON_PATH, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Jackson-friendly POJO mirroring the ETL output JSON schema.
     * Converts to the immutable {@link Branch} record via {@link #toDomain()}.
     */
    private static class BranchJson {
        public String branchId;
        public String name;
        public String brand;
        public String address;
        public String city;
        public String postalCode;
        public double latitude;
        public double longitude;
        public String phone;
        public String openingHours;
        public boolean wheelchairAccessible;
        public List<String> selfServices;
        public List<String> branchServices;
        public Boolean advisoryAvailable;
        public String transitInfo;
        public String parkingInfo;

        Branch toDomain() {
            List<String> copiedBranchServices = branchServices != null ? List.copyOf(branchServices) : List.of();
            return new Branch(
                    branchId,
                    name,
                    brand != null ? brand : "Unknown",
                    address != null ? address : "",
                    city != null ? city : "",
                    postalCode != null ? postalCode : "",
                    latitude,
                    longitude,
                    phone,
                    openingHours,
                    wheelchairAccessible,
                    selfServices != null ? List.copyOf(selfServices) : List.of(),
                    copiedBranchServices,
                    resolveAdvisoryAvailable(advisoryAvailable, copiedBranchServices),
                    transitInfo,
                    parkingInfo
            );
        }

        private static boolean resolveAdvisoryAvailable(Boolean explicitFlag, List<String> branchServices) {
            if (explicitFlag != null) {
                return explicitFlag;
            }
            return branchServices.stream()
                    .filter(Objects::nonNull)
                    .map(service -> service.toLowerCase(Locale.ROOT))
                    .anyMatch(service -> ADVISORY_KEYWORDS.stream().anyMatch(service::contains));
        }
    }

    // ==========================================
    // Centroid Computation
    // ==========================================

    private Map<String, Coordinate> computeCityCentroids() {
        return branches.stream()
                .collect(Collectors.groupingBy(Branch::city))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> {
                            double avgLat = e.getValue().stream()
                                    .mapToDouble(Branch::latitude).average().orElse(0);
                            double avgLon = e.getValue().stream()
                                    .mapToDouble(Branch::longitude).average().orElse(0);
                            return new Coordinate(avgLat, avgLon);
                        }
                ));
    }
}
