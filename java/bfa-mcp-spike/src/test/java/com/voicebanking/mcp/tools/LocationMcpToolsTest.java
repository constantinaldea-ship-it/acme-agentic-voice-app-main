package com.voicebanking.mcp.tools;

import com.voicebanking.mcp.location.BranchDto;
import com.voicebanking.mcp.location.BranchSearchResponse;
import com.voicebanking.mcp.location.LocationService;
import com.voicebanking.mcp.location.BranchSearchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LocationMcpTools}.
 *
 * <p>Validates that MCP tool methods correctly delegate to {@link LocationService}
 * and pass parameters accurately.</p>
 *
 * @author Augment Agent
 * @since 2026-02-09
 */
@ExtendWith(MockitoExtension.class)
class LocationMcpToolsTest {

    @Mock
    private LocationService locationService;

    private LocationMcpTools mcpTools;

    @BeforeEach
    void setUp() {
        mcpTools = new LocationMcpTools(locationService);
    }

    // ==========================================
    // branch_search tool
    // ==========================================

    @Test
    void branchSearchDelegatesToLocationService() {
        BranchSearchResponse mockResponse = new BranchSearchResponse(
                List.of(createTestBranch("DB-001")),
                1, 1,
                new BranchSearchResponse.ReferencePoint(50.9375, 6.9603, "CITY_CENTROID")
        );
        when(locationService.search(any(BranchSearchRequest.class))).thenReturn(mockResponse);

        BranchSearchResponse result = mcpTools.branchSearch(
                "Köln", null, null, null, null, null, null, null, 5);

        assertThat(result).isNotNull();
        assertThat(result.count()).isEqualTo(1);
        assertThat(result.branches()).hasSize(1);
        assertThat(result.branches().get(0).branchId()).isEqualTo("DB-001");

        verify(locationService).search(argThat(req ->
                "Köln".equals(req.city()) && req.limit() == 5));
    }

    @Test
    void branchSearchPassesAllParameters() {
        BranchSearchResponse mockResponse = new BranchSearchResponse(
                List.of(), 0, 0,
                new BranchSearchResponse.ReferencePoint(50.0, 7.0, "PROVIDED_COORDINATES")
        );
        when(locationService.search(any(BranchSearchRequest.class))).thenReturn(mockResponse);

        mcpTools.branchSearch(
                "München", "Maximilianstraße", "80539",
                48.1351, 11.5820, 10.0,
                "Deutsche Bank", true, 3);

        verify(locationService).search(argThat(req ->
                "München".equals(req.city())
                && "Maximilianstraße".equals(req.address())
                && "80539".equals(req.postalCode())
                && req.latitude() == 48.1351
                && req.longitude() == 11.5820
                && req.radiusKm() == 10.0
                && "Deutsche Bank".equals(req.brand())
                && Boolean.TRUE.equals(req.accessible())
                && req.limit() == 3
        ));
    }

    @Test
    void branchSearchHandlesNullParameters() {
        BranchSearchResponse mockResponse = new BranchSearchResponse(
                List.of(), 0, 0,
                new BranchSearchResponse.ReferencePoint(51.0, 10.0, "DATA_CENTROID")
        );
        when(locationService.search(any(BranchSearchRequest.class))).thenReturn(mockResponse);

        BranchSearchResponse result = mcpTools.branchSearch(
                null, null, null, null, null, null, null, null, null);

        assertThat(result).isNotNull();
        verify(locationService).search(argThat(req ->
                req.city() == null && req.address() == null && req.limit() == null));
    }

    // ==========================================
    // branch_details tool
    // ==========================================

    @Test
    void branchDetailsDelegatesToLocationService() {
        BranchDto mockBranch = createTestBranch("DB-001");
        when(locationService.getBranch("DB-001")).thenReturn(mockBranch);

        BranchDto result = mcpTools.branchDetails("DB-001");

        assertThat(result).isNotNull();
        assertThat(result.branchId()).isEqualTo("DB-001");
        assertThat(result.name()).isEqualTo("Test Branch Köln");
        verify(locationService).getBranch("DB-001");
    }

    @Test
    void branchDetailsThrowsOnUnknownBranch() {
        when(locationService.getBranch("NONEXISTENT")).thenThrow(
                new RuntimeException("Branch not found: NONEXISTENT"));

        assertThatThrownBy(() -> mcpTools.branchDetails("NONEXISTENT"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("NONEXISTENT");
    }

    // ==========================================
    // Service-layer reuse validation
    // ==========================================

    @Test
    void mcpToolUsingSameServiceInstanceAsInjected() {
        assertThat(mcpTools).hasFieldOrPropertyWithValue("locationService", locationService);
    }

    // ==========================================
    // Helpers
    // ==========================================

    private BranchDto createTestBranch(String id) {
        return new BranchDto(
                id, "Test Branch Köln", "Deutsche Bank",
                "Hauptstraße 1", "Köln", "50667",
                50.9375, 6.9603,
                "+49 221 123456", "Mo-Fr 09:00-16:00",
                true,
                List.of("Bargeldauszahlung"), List.of("Wertschließfächer"),
                "U-Bahn: Dom/Hbf", "Parkhaus Schildergasse",
                2.5
        );
    }
}
