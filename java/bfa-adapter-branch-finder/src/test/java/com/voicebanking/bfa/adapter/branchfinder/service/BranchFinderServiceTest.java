package com.voicebanking.bfa.adapter.branchfinder.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link BranchFinderService}.
 *
 * @author Copilot
 * @since 2026-03-01
 */
class BranchFinderServiceTest {

    private BranchFinderService service;

    @BeforeEach
    void setUp() {
        service = new BranchFinderService();
    }

    // ──────────────────────────────────────────────────────────────
    // Happy path
    // ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("search — happy path")
    class HappyPath {

        @Test
        @DisplayName("returns branches for known city")
        void returnsBranchesForKnownCity() {
            Map<String, Object> result = service.search("Frankfurt", 5, "corr-001");

            assertEquals("Frankfurt", result.get("city"));
            assertInstanceOf(Integer.class, result.get("totalMatches"));
            assertTrue((int) result.get("totalMatches") > 0);
            assertInstanceOf(List.class, result.get("branches"));
        }

        @Test
        @DisplayName("respects limit parameter")
        void respectsLimit() {
            Map<String, Object> result = service.search("Frankfurt", 1, "corr-002");
            assertEquals(1, result.get("count"));
        }

        @Test
        @DisplayName("returns empty list for unknown city")
        void returnsEmptyForUnknownCity() {
            Map<String, Object> result = service.search("Atlantis", 5, "corr-003");
            assertEquals(0, result.get("totalMatches"));
            assertEquals(0, result.get("count"));
        }

        @Test
        @DisplayName("city search is case-insensitive")
        void citySearchIsCaseInsensitive() {
            Map<String, Object> upper = service.search("BERLIN", 5, "corr-004");
            Map<String, Object> lower = service.search("berlin", 5, "corr-005");
            Map<String, Object> mixed = service.search("Berlin", 5, "corr-006");

            assertEquals(upper.get("totalMatches"), lower.get("totalMatches"));
            assertEquals(upper.get("totalMatches"), mixed.get("totalMatches"));
        }

        @Test
        @DisplayName("branch data contains expected fields")
        @SuppressWarnings("unchecked")
        void branchDataContainsExpectedFields() {
            Map<String, Object> result = service.search("Hamburg", 5, "corr-007");

            var branches = (List<Map<String, Object>>) result.get("branches");
            assertFalse(branches.isEmpty());

            Map<String, Object> branch = branches.get(0);
            assertNotNull(branch.get("branchId"));
            assertNotNull(branch.get("name"));
            assertNotNull(branch.get("address"));
            assertNotNull(branch.get("postalCode"));
            assertNotNull(branch.get("city"));
            assertNotNull(branch.get("latitude"));
            assertNotNull(branch.get("longitude"));
            assertNotNull(branch.get("accountNumber"));
        }

        @Test
        @DisplayName("covers all four seed cities")
        void coversAllSeedCities() {
            for (String city : List.of("frankfurt", "berlin", "hamburg", "munich")) {
                Map<String, Object> result = service.search(city, 10, "corr-cities");
                assertTrue((int) result.get("totalMatches") > 0,
                        "Expected branches for city: " + city);
            }
        }
    }
}
