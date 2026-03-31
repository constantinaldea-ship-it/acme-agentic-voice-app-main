package com.voicebanking.bfa.adapter.branchfinder.controller;

import com.voicebanking.bfa.adapter.branchfinder.dto.AdapterRequest;
import com.voicebanking.bfa.adapter.branchfinder.dto.AdapterResponse;
import com.voicebanking.bfa.adapter.branchfinder.service.BranchFinderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for the Branch Finder adapter.
 *
 * <p>Exposes actions under {@code /actions/{action}} — the canonical adapter
 * contract that the BFA Gateway calls over HTTP.  The gateway resolves
 * CES tool names to adapter + action pairs, then calls
 * {@code POST {adapterBaseUrl}/actions/{action}}.</p>
 *
 * <p>This adapter has one action: {@code search} (branch/location search).</p>
 *
 * @author Copilot
 * @since 2026-03-01
 * @modified Copilot on 2026-03-02 — refactored from /invoke to /actions/{action}
 */
@RestController
@RequestMapping("/")
@Tag(name = "Branch Finder Adapter", description = "AG-003 — domain adapter for branch/location search")
public class BranchFinderController {

    private static final Logger log = LoggerFactory.getLogger(BranchFinderController.class);

    private final BranchFinderService branchFinderService;

    public BranchFinderController(BranchFinderService branchFinderService) {
        this.branchFinderService = branchFinderService;
    }

    @PostMapping("/actions/search")
    @Operation(summary = "Search branches by city",
            description = "Called by the BFA Gateway via tool route: branchFinder → branch-finder/actions/search. " +
                    "Accepts city + optional limit parameters.")
    public ResponseEntity<AdapterResponse> invoke(@RequestBody AdapterRequest request) {
        String correlationId = request.correlationId() != null ? request.correlationId() : "unknown";
        MDC.put("correlationId", correlationId);

        try {
            Map<String, Object> parameters = request.parameters();
            if (parameters == null) {
                return ResponseEntity.badRequest().body(
                        AdapterResponse.error("INVALID_PARAMETERS",
                                "parameters must not be null", correlationId));
            }

            // ── validate city ──
            Object cityObj = parameters.get("city");
            if (cityObj == null || cityObj.toString().isBlank()) {
                return ResponseEntity.badRequest().body(
                        AdapterResponse.error("INVALID_PARAMETERS",
                                "Parameter 'city' is required", correlationId));
            }
            String city = cityObj.toString().trim();

            // ── validate limit ──
            int limit = 5;
            Object limitObj = parameters.get("limit");
            if (limitObj != null) {
                try {
                    limit = Integer.parseInt(limitObj.toString());
                    if (limit < 1 || limit > 50) {
                        return ResponseEntity.badRequest().body(
                                AdapterResponse.error("INVALID_PARAMETERS",
                                        "Parameter 'limit' must be between 1 and 50", correlationId));
                    }
                } catch (NumberFormatException e) {
                    return ResponseEntity.badRequest().body(
                            AdapterResponse.error("INVALID_PARAMETERS",
                                    "Parameter 'limit' must be an integer", correlationId));
                }
            }

            // ── delegate to service ──
            Map<String, Object> result = branchFinderService.search(city, limit, correlationId);

            log.info("[{}] Branch search completed: city='{}' matches={}",
                    correlationId, city, result.get("totalMatches"));

            return ResponseEntity.ok(AdapterResponse.success(result, correlationId));

        } catch (Exception e) {
            log.error("[{}] Unexpected error: {}", correlationId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    AdapterResponse.error("INTERNAL_ERROR",
                            "Unexpected error in branch-finder adapter", correlationId));
        } finally {
            MDC.remove("correlationId");
        }
    }

    @GetMapping("/health")
    @Operation(summary = "Adapter health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "adapter", "branch-finder",
                "agent", "AG-003"
        ));
    }
}
