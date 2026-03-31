package com.voicebanking.bfa.location;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for BranchRepository — JSON loading, querying, and centroid computation.
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
@SpringBootTest
class BranchRepositoryTest {

    @Autowired
    private BranchRepository repository;

    // ==========================================
    // JSON Loading
    // ==========================================

    @Test
    void shouldLoadBranchesFromJson() {
        assertThat(repository.count()).isGreaterThan(0);
        assertThat(repository.findAll()).isNotEmpty();
    }

    @Test
    void shouldLoad393Branches() {
        assertThat(repository.count()).isEqualTo(393);
    }

    @Test
    void shouldParseBranchFieldsCorrectly() {
        Branch first = repository.findAll().get(0);
        assertThat(first.branchId()).isNotBlank();
        assertThat(first.name()).isNotBlank();
        assertThat(first.brand()).isIn("Deutsche Bank", "Postbank");
        assertThat(first.address()).isNotBlank();
        assertThat(first.city()).isNotBlank();
        assertThat(first.postalCode()).matches("\\d{5}");
        assertThat(first.latitude()).isBetween(47.0, 55.0);  // Germany latitude range
        assertThat(first.longitude()).isBetween(5.0, 16.0);   // Germany longitude range
    }

    @Test
    void shouldLoadBothBrands() {
        Set<String> brands = repository.findAll().stream()
                .map(Branch::brand).collect(Collectors.toSet());
        assertThat(brands).containsExactlyInAnyOrder("Deutsche Bank", "Postbank");
    }

    @Test
    void shouldLoadServicesForAtLeastSomeBranches() {
        long withServices = repository.findAll().stream()
                .filter(b -> !b.selfServices().isEmpty() || !b.branchServices().isEmpty())
                .count();
        assertThat(withServices).isGreaterThan(100);
    }

    @Test
    void shouldDeriveAdvisoryAvailabilityFromBranchServices() {
        Branch advisoryBranch = repository.findAll().stream()
                .filter(Branch::advisoryAvailable)
                .findFirst()
                .orElseThrow();

        assertThat(advisoryBranch.branchServices()).isNotEmpty();
        assertThat(advisoryBranch.branchServices())
                .anyMatch(service -> service.toLowerCase().contains("berat"));
    }

    @Test
    void shouldLoadAccessibilityInfo() {
        long withWheelchair = repository.findAll().stream()
                .filter(Branch::wheelchairAccessible).count();
        assertThat(withWheelchair).isGreaterThan(0);

        long withTransit = repository.findAll().stream()
                .filter(b -> b.transitInfo() != null).count();
        assertThat(withTransit).isGreaterThan(0);
    }

    // ==========================================
    // Find By ID
    // ==========================================

    @Test
    void shouldFindBranchById() {
        Branch first = repository.findAll().get(0);
        Optional<Branch> found = repository.findById(first.branchId());
        assertThat(found).isPresent();
        assertThat(found.get().branchId()).isEqualTo(first.branchId());
    }

    @Test
    void shouldReturnEmptyForUnknownId() {
        Optional<Branch> found = repository.findById("NONEXISTENT-999");
        assertThat(found).isEmpty();
    }

    // ==========================================
    // Find By City
    // ==========================================

    @Test
    void shouldFindBranchesByCity() {
        List<Branch> results = repository.findByCity("Berlin");
        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(b -> b.city().equalsIgnoreCase("Berlin"));
    }

    @Test
    void shouldFindByCityCaseInsensitive() {
        List<Branch> lower = repository.findByCity("berlin");
        List<Branch> upper = repository.findByCity("BERLIN");
        assertThat(lower).hasSameSizeAs(upper);
    }

    @Test
    void shouldFindByCityPartialMatch() {
        // "Frankfurt" should match "Frankfurt am Main"
        List<Branch> results = repository.findByCity("Frankfurt");
        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(b -> b.city().toLowerCase().contains("frankfurt"));
    }

    @Test
    void shouldReturnEmptyForUnknownCity() {
        List<Branch> results = repository.findByCity("Atlantis");
        assertThat(results).isEmpty();
    }

    // ==========================================
    // Find By Postal Code
    // ==========================================

    @Test
    void shouldFindByPostalCode() {
        String plz = repository.findAll().get(0).postalCode();
        List<Branch> results = repository.findByPostalCode(plz);
        assertThat(results).isNotEmpty();
        assertThat(results).allMatch(b -> b.postalCode().startsWith(plz));
    }

    @Test
    void shouldFindByPostalCodePrefix() {
        String plz = repository.findAll().get(0).postalCode();
        String prefix = plz.substring(0, 2);
        List<Branch> results = repository.findByPostalCode(prefix);
        assertThat(results).isNotEmpty();
        assertThat(results.size()).isGreaterThanOrEqualTo(1);
    }

    // ==========================================
    // Find By Address
    // ==========================================

    @Test
    void shouldFindByAddressPartialMatch() {
        Branch first = repository.findAll().get(0);
        String address = first.address();
        String firstWord = address.split("\\s+")[0];
        if (firstWord.length() > 3) {
            List<Branch> results = repository.findByAddress(firstWord);
            assertThat(results).isNotEmpty();
        }
    }

    // ==========================================
    // City Centroids
    // ==========================================

    @Test
    void shouldComputeCityCentroids() {
        Optional<BranchRepository.Coordinate> centroid = repository.getCityCentroid("Berlin");
        assertThat(centroid).isPresent();
        assertThat(centroid.get().latitude()).isBetween(52.0, 53.0);  // Berlin latitude
        assertThat(centroid.get().longitude()).isBetween(13.0, 14.0); // Berlin longitude
    }

    @Test
    void shouldComputeCentroidAsAverageOfBranches() {
        String city = "Hamburg";
        List<Branch> cityBranches = repository.findByCity(city);

        double expectedLat = cityBranches.stream().mapToDouble(Branch::latitude).average().orElse(0);
        double expectedLon = cityBranches.stream().mapToDouble(Branch::longitude).average().orElse(0);

        BranchRepository.Coordinate centroid = repository.getCityCentroid(city).orElseThrow();
        assertThat(centroid.latitude()).isCloseTo(expectedLat, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(centroid.longitude()).isCloseTo(expectedLon, org.assertj.core.data.Offset.offset(0.0001));
    }

    @Test
    void shouldReturnEmptyCentroidForUnknownCity() {
        Optional<BranchRepository.Coordinate> centroid = repository.getCityCentroid("Atlantis");
        assertThat(centroid).isEmpty();
    }

    @Test
    void shouldComputeDataCentroid() {
        BranchRepository.Coordinate centroid = repository.getDataCentroid();
        assertThat(centroid.latitude()).isBetween(47.0, 55.0);
        assertThat(centroid.longitude()).isBetween(5.0, 16.0);
    }

    // ==========================================
    // Multi-city coverage
    // ==========================================

    @Test
    void shouldCoverAtLeast20Cities() {
        Set<String> cities = repository.findAll().stream()
                .map(Branch::city).collect(Collectors.toSet());
        assertThat(cities.size()).isGreaterThanOrEqualTo(20);
    }
}
