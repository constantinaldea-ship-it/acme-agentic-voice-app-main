package com.voicebanking.bfa.location;

import com.voicebanking.bfa.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for LocationService — search orchestration, filtering, and distance sorting.
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
@SpringBootTest
class LocationServiceTest {

    @Autowired
    private LocationService locationService;

    @Autowired
    private BranchRepository branchRepository;

    // ==========================================
    // Basic Search
    // ==========================================

    @Test
    void shouldReturnResultsForEmptySearch() {
        BranchSearchResponse result = locationService.search(
                new BranchSearchRequest(null, null, null, null, null, null, null, null, null));
        assertThat(result.count()).isGreaterThan(0);
        assertThat(result.count()).isLessThanOrEqualTo(10);
        assertThat(result.referencePoint()).isNotNull();
        assertThat(result.referencePoint().source()).isEqualTo("DATA_CENTROID");
    }

    @Test
    void shouldReturnBranchesForEmptySearch() {
        BranchSearchResponse result = locationService.search(
                new BranchSearchRequest(null, null, null, null, null, null, null, null, null));
        assertThat(result.branches()).isNotEmpty();
        assertThat(result.branches().get(0).branchId()).isNotBlank();
    }

    // ==========================================
    // City Search
    // ==========================================

    @Test
    void shouldSearchByCity() {
        BranchSearchResponse result = locationService.search(
                new BranchSearchRequest("Hamburg", null, null, null, null, null, null, null, null));

        assertThat(result.count()).isGreaterThan(0);
        assertThat(result.branches()).allMatch(b -> b.city().toLowerCase().contains("hamburg"));
        assertThat(result.referencePoint().source()).isEqualTo("CITY_CENTROID");
    }

    @Test
    void shouldReturnDistanceSortedResultsForCitySearch() {
        BranchSearchResponse result = locationService.search(
                new BranchSearchRequest("Berlin", null, null, null, null, null, null, null, 50));

        if (result.count() > 1) {
            for (int i = 1; i < result.branches().size(); i++) {
                assertThat(result.branches().get(i).distanceKm())
                        .isGreaterThanOrEqualTo(result.branches().get(i - 1).distanceKm());
            }
        }
    }

    @Test
    void shouldReturnEmptyForUnknownCity() {
        BranchSearchResponse result = locationService.search(
                new BranchSearchRequest("Atlantis", null, null, null, null, null, null, null, null));
        assertThat(result.count()).isEqualTo(0);
        assertThat(result.branches()).isEmpty();
    }

    // ==========================================
    // Coordinate Search
    // ==========================================

    @Test
    void shouldSearchByCoordinates() {
        Branch first = branchRepository.findAll().get(0);
        BranchSearchResponse result = locationService.search(
                new BranchSearchRequest(null, null, null,
                        first.latitude(), first.longitude(),
                        50.0, null, null, null));

        assertThat(result.count()).isGreaterThan(0);
        assertThat(result.referencePoint().source()).isEqualTo("PROVIDED_COORDINATES");
        assertThat(result.branches().get(0).distanceKm()).isLessThan(1.0);
    }

    @Test
    void shouldFilterByRadiusWhenCoordinatesProvided() {
        Branch first = branchRepository.findAll().get(0);
        BranchSearchResponse narrow = locationService.search(
                new BranchSearchRequest(null, null, null,
                        first.latitude(), first.longitude(),
                        0.1, null, null, null));
        BranchSearchResponse wide = locationService.search(
                new BranchSearchRequest(null, null, null,
                        first.latitude(), first.longitude(),
                        500.0, null, null, 50));

        assertThat(wide.totalMatches()).isGreaterThanOrEqualTo(narrow.totalMatches());
    }

    // ==========================================
    // Postal Code Search
    // ==========================================

    @Test
    void shouldSearchByPostalCode() {
        String plz = branchRepository.findAll().get(0).postalCode();
        BranchSearchResponse result = locationService.search(
                new BranchSearchRequest(null, null, plz, null, null, null, null, null, null));

        assertThat(result.count()).isGreaterThan(0);
        assertThat(result.referencePoint().source()).isEqualTo("POSTAL_CODE_CENTROID");
    }

    // ==========================================
    // Brand Filtering
    // ==========================================

    @Test
    void shouldFilterByBrand() {
        BranchSearchResponse dbOnly = locationService.search(
                new BranchSearchRequest(null, null, null, null, null, null,
                        "Deutsche Bank", null, 50));

        assertThat(dbOnly.count()).isGreaterThan(0);
        assertThat(dbOnly.branches()).allMatch(b -> b.brand().equals("Deutsche Bank"));
    }

    @Test
    void shouldFilterByPostbankBrand() {
        BranchSearchResponse postbank = locationService.search(
                new BranchSearchRequest(null, null, null, null, null, null,
                        "Postbank", null, 50));

        assertThat(postbank.count()).isGreaterThan(0);
        assertThat(postbank.branches()).allMatch(b -> b.brand().equals("Postbank"));
    }

    @Test
    void shouldIgnoreUnknownBrand() {
        BranchSearchResponse result = locationService.search(
                new BranchSearchRequest(null, null, null, null, null, null,
                        "UnknownBrand", null, null));
        // Unknown brand filters to empty
        assertThat(result.count()).isEqualTo(0);
    }

    // ==========================================
    // Accessibility Filtering
    // ==========================================

    @Test
    void shouldFilterByAccessibility() {
        BranchSearchResponse accessible = locationService.search(
                new BranchSearchRequest(null, null, null, null, null, null,
                        null, true, 50));
        BranchSearchResponse all = locationService.search(
                new BranchSearchRequest(null, null, null, null, null, null,
                        null, null, 50));

        assertThat(accessible.totalMatches()).isLessThanOrEqualTo(all.totalMatches());
        assertThat(accessible.branches()).allMatch(BranchDto::wheelchairAccessible);
    }

    // ==========================================
    // Limit
    // ==========================================

    @Test
    void shouldRespectLimit() {
        BranchSearchResponse result = locationService.search(
                new BranchSearchRequest(null, null, null, null, null, null, null, null, 3));
        assertThat(result.count()).isLessThanOrEqualTo(3);
    }

    @Test
    void shouldDefaultLimitTo10() {
        BranchSearchResponse result = locationService.search(
                new BranchSearchRequest(null, null, null, null, null, null, null, null, null));
        assertThat(result.count()).isLessThanOrEqualTo(10);
    }

    @Test
    void shouldCapLimitAt50() {
        BranchSearchResponse result = locationService.search(
                new BranchSearchRequest(null, null, null, null, null, null, null, null, 999));
        assertThat(result.count()).isLessThanOrEqualTo(50);
    }

    // ==========================================
    // Combined Filters
    // ==========================================

    @Test
    void shouldCombineCityAndBrandFilters() {
        BranchSearchResponse result = locationService.search(
                new BranchSearchRequest("Berlin", null, null, null, null, null,
                        "Postbank", null, null));

        assertThat(result.branches()).allMatch(b ->
                b.city().toLowerCase().contains("berlin")
                && b.brand().equals("Postbank"));
    }

    // ==========================================
    // Get Single Branch
    // ==========================================

    @Test
    void shouldGetBranchById() {
        Branch first = branchRepository.findAll().get(0);
        BranchDto dto = locationService.getBranch(first.branchId());

        assertThat(dto.branchId()).isEqualTo(first.branchId());
        assertThat(dto.name()).isEqualTo(first.name());
        assertThat(dto.advisoryAvailable()).isEqualTo(first.advisoryAvailable());
        assertThat(dto.distanceKm()).isNull();
    }

    @Test
    void shouldThrowForUnknownBranch() {
        assertThatThrownBy(() -> locationService.getBranch("NONEXISTENT-999"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ==========================================
    // Haversine Distance
    // ==========================================

    @Test
    void shouldCalculateZeroDistanceForSamePoint() {
        double distance = LocationService.haversine(50.0, 10.0, 50.0, 10.0);
        assertThat(distance).isEqualTo(0.0);
    }

    @Test
    void shouldCalculateReasonableDistance() {
        // Berlin (52.52, 13.405) to Munich (48.1351, 11.582) ≈ 504 km
        double distance = LocationService.haversine(52.52, 13.405, 48.1351, 11.582);
        assertThat(distance).isBetween(500.0, 510.0);
    }

    // ==========================================
    // totalMatches vs count
    // ==========================================

    @Test
    void shouldReportTotalMatchesSeparateFromCount() {
        BranchSearchResponse result = locationService.search(
                new BranchSearchRequest("Berlin", null, null, null, null, null, null, null, 2));

        assertThat(result.count()).isLessThanOrEqualTo(2);
        assertThat(result.totalMatches()).isGreaterThanOrEqualTo(result.count());
    }

    // ==========================================
    // Enriched Data
    // ==========================================

    @Test
    void shouldReturnServicesInDto() {
        // Find a branch with services
        Branch withServices = branchRepository.findAll().stream()
                .filter(b -> !b.selfServices().isEmpty() && !b.branchServices().isEmpty())
                .findFirst().orElseThrow();
        BranchDto dto = locationService.getBranch(withServices.branchId());
        assertThat(dto.selfServices()).isNotNull();
        assertThat(dto.selfServices()).isNotEmpty();
        assertThat(dto.branchServices()).isNotNull();
        assertThat(dto.branchServices()).isNotEmpty();
    }

    @Test
    void shouldReturnAdvisoryAvailabilityInDto() {
        Branch advisoryBranch = branchRepository.findAll().stream()
                .filter(Branch::advisoryAvailable)
                .findFirst().orElseThrow();

        BranchDto dto = locationService.getBranch(advisoryBranch.branchId());
        assertThat(dto.advisoryAvailable()).isTrue();
    }

    @Test
    void shouldReturnBrandInDto() {
        Branch first = branchRepository.findAll().get(0);
        BranchDto dto = locationService.getBranch(first.branchId());
        assertThat(dto.brand()).isIn("Deutsche Bank", "Postbank");
    }
}
