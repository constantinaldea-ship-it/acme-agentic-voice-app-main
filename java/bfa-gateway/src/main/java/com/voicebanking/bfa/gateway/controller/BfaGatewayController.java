package com.voicebanking.bfa.gateway.controller;

import com.voicebanking.bfa.gateway.adapter.AdapterRegistry;
import com.voicebanking.bfa.gateway.authz.AuthzPdpService;
import com.voicebanking.bfa.gateway.dto.ErrorResponse;
import com.voicebanking.bfa.gateway.dto.ToolInvokeRequest;
import com.voicebanking.bfa.gateway.dto.ToolInvokeResponse;
import com.voicebanking.bfa.gateway.filter.EdgePepFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Single-ingress gateway controller (ADR-0104 Option C).
 *
 * <p>Receives tool invocation requests from the CES Agent, resolves the
 * correct <em>remote</em> domain adapter via the {@link AdapterRegistry},
 * evaluates AuthZ, delegates execution over HTTP, and returns a normalised
 * response.</p>
 *
 * <h3>Request flow</h3>
 * <pre>
 *   Client → EdgePepFilter → Controller → AuthzPdp → AdapterRegistry
 *          → HTTP POST to remote adapter → ResponsePepFilter → Client
 * </pre>
 *
 * @author Copilot
 * @since 2026-01-17
 * @modified Copilot on 2026-03-01 — refactored to HTTP-based adapter invocation (Option C)
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "BFA Gateway", description = "Single-ingress tool invocation gateway (ADR-0104 Option C)")
public class BfaGatewayController {

    private static final Logger log = LoggerFactory.getLogger(BfaGatewayController.class);

    private final AdapterRegistry adapterRegistry;
    private final AuthzPdpService authzPdpService;

    public BfaGatewayController(AdapterRegistry adapterRegistry,
                                 AuthzPdpService authzPdpService) {
        this.adapterRegistry = adapterRegistry;
        this.authzPdpService = authzPdpService;
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Tool Invocation
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @PostMapping("/tools/invoke")
    @Operation(
            summary = "Invoke a domain tool",
            description = """
                    Routes the request to the appropriate remote domain adapter
                    identified by `toolName`. The gateway enforces Edge PEP
                    (authentication), AuthZ PDP (authorization), and Response PEP
                    (PII masking) around the HTTP call to the adapter service.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tool invocation succeeded"),
            @ApiResponse(responseCode = "400", description = "Invalid request or adapter parameter validation failed"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid Bearer token (Edge PEP)"),
            @ApiResponse(responseCode = "403", description = "AuthZ PDP denied the request"),
            @ApiResponse(responseCode = "404", description = "No adapter registered for the given toolName"),
            @ApiResponse(responseCode = "502", description = "Remote adapter returned an error or is unreachable")
    })
    @SuppressWarnings("unchecked")
    public ResponseEntity<ToolInvokeResponse> invoke(
            @Valid @RequestBody ToolInvokeRequest request,
            HttpServletRequest httpRequest) {

        String correlationId = correlationId(httpRequest);
        String principal = principal(httpRequest);

        log.info("[{}] Tool invoke: tool='{}' principal='{}'",
                correlationId, request.toolName(), principal);

        // ── 1. Check adapter exists ──
        if (!adapterRegistry.hasAdapter(request.toolName())) {
            log.warn("[{}] No adapter registered for tool '{}'", correlationId, request.toolName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ToolInvokeResponse.failure(
                            request.toolName(),
                            ErrorResponse.of("ADAPTER_NOT_FOUND",
                                    "No adapter registered for tool '%s'. Available tools: %s"
                                            .formatted(request.toolName(), adapterRegistry.registeredTools())),
                            correlationId));
        }

        // ── 2. AuthZ check ──
        AuthzPdpService.Decision decision = authzPdpService.evaluate(
                principal, request.toolName(), correlationId);

        if (decision == AuthzPdpService.Decision.DENY) {
            log.warn("[{}] AuthZ DENIED: principal='{}' tool='{}'",
                    correlationId, principal, request.toolName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ToolInvokeResponse.failure(
                            request.toolName(),
                            ErrorResponse.of("AUTHORIZATION_DENIED",
                                    "Principal '%s' is not authorised to invoke tool '%s'"
                                            .formatted(principal, request.toolName())),
                            correlationId));
        }

        // ── 3. Delegate to remote adapter over HTTP ──
        try {
            Map<String, Object> adapterResponse = adapterRegistry.invoke(
                    request.toolName(), request.parameters(), correlationId);

            // The adapter returns {success, data, errorCode, errorMessage, ...}
            Boolean adapterSuccess = (Boolean) adapterResponse.get("success");

            if (Boolean.TRUE.equals(adapterSuccess)) {
                Map<String, Object> data = (Map<String, Object>) adapterResponse.get("data");
                log.info("[{}] Tool '{}' completed successfully", correlationId, request.toolName());
                return ResponseEntity.ok(
                        ToolInvokeResponse.success(request.toolName(), data, correlationId));
            } else {
                String errorCode = (String) adapterResponse.getOrDefault("errorCode", "ADAPTER_ERROR");
                String errorMessage = (String) adapterResponse.getOrDefault("errorMessage", "Adapter reported failure");
                log.warn("[{}] Adapter '{}' reported failure: {} — {}",
                        correlationId, request.toolName(), errorCode, errorMessage);
                return ResponseEntity.badRequest()
                        .body(ToolInvokeResponse.failure(
                                request.toolName(),
                                ErrorResponse.of(errorCode, errorMessage),
                                correlationId));
            }

        } catch (AdapterRegistry.AdapterNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ToolInvokeResponse.failure(
                            request.toolName(),
                            ErrorResponse.of("ADAPTER_NOT_FOUND", e.getMessage()),
                            correlationId));

        } catch (AdapterRegistry.AdapterInvocationException e) {
            log.error("[{}] Adapter '{}' invocation failed: {}",
                    correlationId, request.toolName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(ToolInvokeResponse.failure(
                            request.toolName(),
                            ErrorResponse.of("ADAPTER_UNREACHABLE",
                                    "Remote adapter '%s' is unreachable or returned an error"
                                            .formatted(request.toolName())),
                            correlationId));

        } catch (Exception e) {
            log.error("[{}] Adapter '{}' failed unexpectedly: {}",
                    correlationId, request.toolName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ToolInvokeResponse.failure(
                            request.toolName(),
                            ErrorResponse.of("ADAPTER_ERROR",
                                    "Internal error invoking tool '%s'"
                                            .formatted(request.toolName())),
                            correlationId));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Discovery & Health
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @GetMapping("/tools")
    @Operation(summary = "List registered tools",
            description = "Returns all CES tool names and their adapter routing.")
    public ResponseEntity<Map<String, Object>> listTools() {
        List<String> tools = adapterRegistry.registeredTools();
        return ResponseEntity.ok(Map.of(
                "count", tools.size(),
                "tools", tools,
                "adapters", adapterRegistry.registeredAdapters()
        ));
    }

    @GetMapping("/health")
    @Operation(summary = "Gateway health check")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "bfa-gateway",
                "tools", adapterRegistry.registeredTools().size(),
                "adapters", adapterRegistry.registeredAdapters().size()
        ));
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Helpers
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private static String correlationId(HttpServletRequest request) {
        Object id = request.getAttribute(EdgePepFilter.ATTR_CORRELATION_ID);
        return id != null ? id.toString() : "unknown";
    }

    private static String principal(HttpServletRequest request) {
        Object p = request.getAttribute(EdgePepFilter.ATTR_PRINCIPAL);
        return p != null ? p.toString() : "anonymous";
    }
}
