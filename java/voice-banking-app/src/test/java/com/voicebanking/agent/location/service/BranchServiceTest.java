package com.voicebanking.agent.location.service;

import com.voicebanking.agent.location.domain.Branch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BranchService Unit Tests
 */
class BranchServiceTest {

    private BranchService branchService;

    @BeforeEach
    void setUp() {
        branchService = new BranchService();
    }

    @Test
    void shouldInitializeWithBranches() {
        List<Branch> allBranches = branchService.getAllBranches();
        
        assertThat(allBranches).isNotEmpty();
        assertThat(allBranches.size()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void shouldFindBranchesNearFrankfurt() {
        // Frankfurt center coordinates
        double lat = 50.1109;
        double lon = 8.6821;

        List<Branch> nearby = branchService.findNearby(lat, lon, 5.0, 10, null);

        assertThat(nearby).isNotEmpty();
        // Frankfurt should have the flagship branch
        assertThat(nearby.get(0).city()).isEqualTo("Frankfurt am Main");
        // Results should be sorted by distance
        for (int i = 1; i < nearby.size(); i++) {
            assertThat(nearby.get(i).distanceKm())
                .isGreaterThanOrEqualTo(nearby.get(i - 1).distanceKm());
        }
    }

    @Test
    void shouldFilterByType() {
        double lat = 50.1109;
        double lon = 8.6821;

        List<Branch> atmsOnly = branchService.findNearby(lat, lon, 10.0, 10, "atm");

        assertThat(atmsOnly).isNotEmpty();
        assertThat(atmsOnly).allMatch(b -> b.type().equals("atm"));
    }

    @Test
    void shouldFilterByBranchType() {
        double lat = 50.1109;
        double lon = 8.6821;

        List<Branch> branchesOnly = branchService.findNearby(lat, lon, 10.0, 10, "branch");

        assertThat(branchesOnly).isNotEmpty();
        assertThat(branchesOnly).allMatch(b -> b.type().equals("branch"));
    }

    @Test
    void shouldFilterByFlagshipType() {
        double lat = 50.1109;
        double lon = 8.6821;

        List<Branch> flagshipOnly = branchService.findNearby(lat, lon, 10.0, 10, "flagship");

        assertThat(flagshipOnly).isNotEmpty();
        assertThat(flagshipOnly).allMatch(b -> b.type().equals("flagship"));
    }

    @Test
    void shouldReturnAllTypesWhenTypeIsAll() {
        double lat = 50.1109;
        double lon = 8.6821;

        List<Branch> all = branchService.findNearby(lat, lon, 10.0, 10, "all");

        assertThat(all).isNotEmpty();
        // Should have mixed types
        List<String> types = all.stream().map(Branch::type).distinct().toList();
        assertThat(types.size()).isGreaterThan(1);
    }

    @Test
    void shouldLimitResults() {
        double lat = 50.1109;
        double lon = 8.6821;

        List<Branch> limited = branchService.findNearby(lat, lon, 100.0, 2, null);

        assertThat(limited).hasSize(2);
    }

    @Test
    void shouldFilterByRadius() {
        double lat = 50.1109;
        double lon = 8.6821;

        // Very small radius should exclude some branches
        List<Branch> nearby = branchService.findNearby(lat, lon, 0.5, 10, null);

        assertThat(nearby).allMatch(b -> b.distanceKm() <= 0.5);
    }

    @Test
    void shouldReturnEmptyForLocationWithNoBranches() {
        // Middle of the ocean
        double lat = 0.0;
        double lon = 0.0;

        List<Branch> nearby = branchService.findNearby(lat, lon, 5.0, 10, null);

        assertThat(nearby).isEmpty();
    }

    @Test
    void shouldFindBranchesNearMunich() {
        // Munich coordinates
        double lat = 48.1371;
        double lon = 11.5754;

        List<Branch> nearby = branchService.findNearby(lat, lon, 5.0, 10, null);

        assertThat(nearby).isNotEmpty();
        assertThat(nearby.get(0).city()).isEqualTo("München");
    }

    @Test
    void shouldFindBranchesNearBerlin() {
        // Berlin coordinates
        double lat = 52.5170;
        double lon = 13.3888;

        List<Branch> nearby = branchService.findNearby(lat, lon, 10.0, 10, null);

        assertThat(nearby).isNotEmpty();
        assertThat(nearby.get(0).city()).isEqualTo("Berlin");
    }

    @Test
    void shouldCalculateDistanceCorrectly() {
        // Frankfurt center to Sachsenhausen is roughly 1.2 km
        double lat = 50.1109;
        double lon = 8.6821;

        List<Branch> nearby = branchService.findNearby(lat, lon, 5.0, 10, null);
        
        // Find Sachsenhausen branch
        Branch sachsenhausen = nearby.stream()
            .filter(b -> b.name().contains("Sachsenhausen"))
            .findFirst()
            .orElse(null);

        assertThat(sachsenhausen).isNotNull();
        assertThat(sachsenhausen.distanceKm()).isBetween(0.5, 2.0);
    }
}
