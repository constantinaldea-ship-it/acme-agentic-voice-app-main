package com.voicebanking.bfa.location;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for LocationController.
 *
 * <p>Tests the full request chain: BfaSecurityFilter → ConsentInterceptor →
 * AuditInterceptor → Controller → Service → Repository.</p>
 *
 * @author Augment Agent
 * @since 2026-02-07
 */
@SpringBootTest
@AutoConfigureMockMvc
class LocationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BranchRepository branchRepository;

    private static final String AUTH_HEADER = "Authorization";
    private static final String AUTH_TOKEN = "Bearer user-test-001";

    // ==========================================
    // Authentication
    // ==========================================

    @Test
    void searchBranchesRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/branches")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getBranchRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/branches/20337740")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ==========================================
    // Search Branches — Basic
    // ==========================================

    @Test
    void searchBranchesReturnsResults() throws Exception {
        mockMvc.perform(get("/api/v1/branches")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.branches").isArray())
                .andExpect(jsonPath("$.data.count").isNumber())
                .andExpect(jsonPath("$.data.totalMatches").isNumber())
                .andExpect(jsonPath("$.data.referencePoint").exists())
                .andExpect(jsonPath("$.data.referencePoint.latitude").isNumber())
                .andExpect(jsonPath("$.data.referencePoint.longitude").isNumber())
                .andExpect(jsonPath("$.data.referencePoint.source").isString())
                .andExpect(jsonPath("$.meta.correlationId").isString());
    }

    // ==========================================
    // Search Branches — City Filter
    // ==========================================

    @Test
    void searchByCityReturnsFilteredResults() throws Exception {
        mockMvc.perform(get("/api/v1/branches")
                        .param("city", "Hamburg")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count", greaterThan(0)))
                .andExpect(jsonPath("$.data.referencePoint.source").value("CITY_CENTROID"))
                .andExpect(jsonPath("$.data.branches[0].city",
                        containsStringIgnoringCase("Hamburg")));
    }

    @Test
    void searchByUnknownCityReturnsEmptyResults() throws Exception {
        mockMvc.perform(get("/api/v1/branches")
                        .param("city", "Atlantis")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.branches").isEmpty());
    }

    // ==========================================
    // Search Branches — Coordinate Filter
    // ==========================================

    @Test
    void searchByCoordinatesReturnsDistanceSortedResults() throws Exception {
        Branch first = branchRepository.findAll().get(0);

        mockMvc.perform(get("/api/v1/branches")
                        .param("latitude", String.valueOf(first.latitude()))
                        .param("longitude", String.valueOf(first.longitude()))
                        .param("radiusKm", "50")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referencePoint.source").value("PROVIDED_COORDINATES"))
                .andExpect(jsonPath("$.data.branches[0].distanceKm", lessThan(1.0)));
    }

    @Test
    void searchWithLatitudeOnlyReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/branches")
                        .param("latitude", "52.52")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_COORDINATES"));
    }

    @Test
    void searchWithLongitudeOnlyReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/branches")
                        .param("longitude", "13.405")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_COORDINATES"));
    }

    // ==========================================
    // Search Branches — Brand Filter
    // ==========================================

    @Test
    void searchByBrandReturnsFilteredResults() throws Exception {
        mockMvc.perform(get("/api/v1/branches")
                        .param("brand", "Deutsche Bank")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count", greaterThan(0)))
                .andExpect(jsonPath("$.data.branches[*].brand",
                        everyItem(is("Deutsche Bank"))));
    }

    @Test
    void searchByPostbankBrandReturnsFilteredResults() throws Exception {
        mockMvc.perform(get("/api/v1/branches")
                        .param("brand", "Postbank")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count", greaterThan(0)))
                .andExpect(jsonPath("$.data.branches[*].brand",
                        everyItem(is("Postbank"))));
    }

    // ==========================================
    // Search Branches — Accessibility Filter
    // ==========================================

    @Test
    void searchByAccessibilityReturnsOnlyAccessibleBranches() throws Exception {
        mockMvc.perform(get("/api/v1/branches")
                        .param("accessible", "true")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branches[*].wheelchairAccessible",
                        everyItem(is(true))));
    }

    // ==========================================
    // Search Branches — Limit
    // ==========================================

    @Test
    void searchRespectsLimit() throws Exception {
        mockMvc.perform(get("/api/v1/branches")
                        .param("limit", "3")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count", lessThanOrEqualTo(3)))
                .andExpect(jsonPath("$.data.branches", hasSize(lessThanOrEqualTo(3))));
    }

    // ==========================================
    // Search Branches — Combined Filters
    // ==========================================

    @Test
    void searchCombinesCityAndBrand() throws Exception {
        mockMvc.perform(get("/api/v1/branches")
                        .param("city", "Berlin")
                        .param("brand", "Postbank")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branches[*].brand",
                        everyItem(is("Postbank"))));
    }

    // ==========================================
    // Search Branches — Response Structure
    // ==========================================

    @Test
    void searchBranchResponseHasAllRequiredFields() throws Exception {
        mockMvc.perform(get("/api/v1/branches")
                        .param("city", "Hamburg")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.branches[0].branchId").isString())
                .andExpect(jsonPath("$.data.branches[0].name").isString())
                .andExpect(jsonPath("$.data.branches[0].brand").isString())
                .andExpect(jsonPath("$.data.branches[0].address").isString())
                .andExpect(jsonPath("$.data.branches[0].city").isString())
                .andExpect(jsonPath("$.data.branches[0].postalCode").isString())
                .andExpect(jsonPath("$.data.branches[0].latitude").isNumber())
                .andExpect(jsonPath("$.data.branches[0].longitude").isNumber())
                .andExpect(jsonPath("$.data.branches[0].advisoryAvailable").isBoolean())
                .andExpect(jsonPath("$.data.branches[0].distanceKm").isNumber());
    }

    // ==========================================
    // Get Single Branch
    // ==========================================

    @Test
    void getBranchByIdReturnsDetails() throws Exception {
        String branchId = branchRepository.findAll().get(0).branchId();

        mockMvc.perform(get("/api/v1/branches/{branchId}", branchId)
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.branchId").value(branchId))
                .andExpect(jsonPath("$.data.name").isString())
                .andExpect(jsonPath("$.data.brand").isString())
                .andExpect(jsonPath("$.data.advisoryAvailable").isBoolean())
                .andExpect(jsonPath("$.meta.correlationId").isString());
    }

    @Test
    void getBranchByIdReturns404ForUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/branches/{branchId}", "NONEXISTENT-999")
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void getBranchByIdDoesNotIncludeDistance() throws Exception {
        String branchId = branchRepository.findAll().get(0).branchId();

        mockMvc.perform(get("/api/v1/branches/{branchId}", branchId)
                        .header(AUTH_HEADER, AUTH_TOKEN)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.distanceKm").doesNotExist());
    }

    // ==========================================
    // OpenAPI Spec Accessibility
    // ==========================================

    @Test
    void openApiSpecIsAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api-docs/location-services")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Acme Bank Location Services API"))
                .andExpect(jsonPath("$.paths./api/v1/branches").exists())
                .andExpect(jsonPath("$.paths./api/v1/branches/{branchId}").exists());
    }
}
