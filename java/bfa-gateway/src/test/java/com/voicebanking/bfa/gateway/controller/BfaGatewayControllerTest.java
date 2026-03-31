package com.voicebanking.bfa.gateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebanking.bfa.gateway.adapter.AdapterRegistry;
import com.voicebanking.bfa.gateway.dto.ToolInvokeRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link BfaGatewayController}.
 *
 * <p>The {@code AdapterRegistry} is mocked via {@code @MockBean} — this is
 * correct for Option C because the registry now makes HTTP calls to remote
 * adapter services, and we don't want to start real adapter processes during
 * gateway unit tests.  The adapter's own tests live in the
 * {@code bfa-adapter-branch-finder} module.</p>
 *
 * <p>Tool names reflect the CES-facing tool identifiers (e.g.,
 * {@code branchFinder}), which the registry maps to adapter + action
 * pairs internally.</p>
 *
 * @author Copilot
 * @since 2026-01-17
 * @modified Copilot on 2026-03-01 — refactored to mock HTTP-based AdapterRegistry
 * @modified Copilot on 2026-03-02 — updated for action-routed N:1 tool-to-adapter mapping
 */
@SpringBootTest
@AutoConfigureMockMvc
class BfaGatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdapterRegistry adapterRegistry;

    private static final String INVOKE_URI = "/api/v1/tools/invoke";
    private static final String AUTH_HEADER = "Authorization";
    private static final String AUTH_TOKEN = "Bearer test-user-001";

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Happy Path
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /api/v1/tools/invoke — happy path")
    class HappyPath {

        @Test
        @DisplayName("returns 200 with branches for known city")
        void invokeBranchFinderSuccess() throws Exception {
            // Simulate adapter response: {success: true, data: {...}}
            Map<String, Object> adapterResponse = Map.of(
                    "success", true,
                    "data", Map.of(
                            "city", "Frankfurt",
                            "totalMatches", 3,
                            "count", 3,
                            "branches", List.of(
                                    Map.of("branchId", "FRA-001", "name", "Frankfurt Hauptwache",
                                            "accountNumber", "DE89 3704 0044 0532 0130 00")
                            )
                    ),
                    "correlationId", "mocked-corr"
            );

            when(adapterRegistry.hasAdapter("branchFinder")).thenReturn(true);
            when(adapterRegistry.invoke(eq("branchFinder"), anyMap(), anyString()))
                    .thenReturn(adapterResponse);

            ToolInvokeRequest request = new ToolInvokeRequest(
                    "branchFinder", Map.of("city", "Frankfurt"));

            mockMvc.perform(post(INVOKE_URI)
                            .header(AUTH_HEADER, AUTH_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.toolName").value("branchFinder"))
                    .andExpect(jsonPath("$.data.city").value("Frankfurt"))
                    .andExpect(jsonPath("$.data.totalMatches", greaterThan(0)))
                    .andExpect(jsonPath("$.data.branches").isArray())
                    .andExpect(jsonPath("$.data.branches[0].branchId").exists())
                    .andExpect(jsonPath("$.correlationId").isString());
        }

        @Test
        @DisplayName("returns empty list for unknown city")
        void invokeBranchFinderEmptyCity() throws Exception {
            Map<String, Object> adapterResponse = Map.of(
                    "success", true,
                    "data", Map.of(
                            "city", "Atlantis",
                            "totalMatches", 0,
                            "count", 0,
                            "branches", List.of()
                    ),
                    "correlationId", "mocked-corr"
            );

            when(adapterRegistry.hasAdapter("branchFinder")).thenReturn(true);
            when(adapterRegistry.invoke(eq("branchFinder"), anyMap(), anyString()))
                    .thenReturn(adapterResponse);

            ToolInvokeRequest request = new ToolInvokeRequest(
                    "branchFinder", Map.of("city", "Atlantis"));

            mockMvc.perform(post(INVOKE_URI)
                            .header(AUTH_HEADER, AUTH_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalMatches").value(0))
                    .andExpect(jsonPath("$.data.branches").isEmpty());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Authentication (Edge PEP)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Edge PEP — authentication")
    class Authentication {

        @Test
        @DisplayName("returns 401 when no Authorization header")
        void rejects401WithoutToken() throws Exception {
            ToolInvokeRequest request = new ToolInvokeRequest(
                    "branchFinder", Map.of("city", "Berlin"));

            mockMvc.perform(post(INVOKE_URI)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("AUTHENTICATION_REQUIRED"));
        }

        @Test
        @DisplayName("returns 401 with empty Bearer token")
        void rejects401WithEmptyBearer() throws Exception {
            ToolInvokeRequest request = new ToolInvokeRequest(
                    "branchFinder", Map.of("city", "Berlin"));

            mockMvc.perform(post(INVOKE_URI)
                            .header(AUTH_HEADER, "Bearer ")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Adapter not found (404)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /api/v1/tools/invoke — unknown tool")
    class AdapterNotFound {

        @Test
        @DisplayName("returns 404 for unregistered tool")
        void returns404ForUnknownTool() throws Exception {
            when(adapterRegistry.hasAdapter("unknown-tool")).thenReturn(false);
            when(adapterRegistry.registeredTools()).thenReturn(List.of("branchFinder"));

            ToolInvokeRequest request = new ToolInvokeRequest(
                    "unknown-tool", Map.of("key", "value"));

            mockMvc.perform(post(INVOKE_URI)
                            .header(AUTH_HEADER, AUTH_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("ADAPTER_NOT_FOUND"))
                    .andExpect(jsonPath("$.error.message", containsString("unknown-tool")));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Validation errors (400)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /api/v1/tools/invoke — validation")
    class ValidationErrors {

        @Test
        @DisplayName("returns 400 for missing toolName")
        void returns400ForMissingToolName() throws Exception {
            String body = """
                    {"parameters": {"city": "Berlin"}}
                    """;

            mockMvc.perform(post(INVOKE_URI)
                            .header(AUTH_HEADER, AUTH_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("returns 400 when adapter reports parameter error")
        void returns400ForAdapterValidationError() throws Exception {
            // Adapter returns a failure response for missing 'city' param
            Map<String, Object> adapterResponse = Map.of(
                    "success", false,
                    "errorCode", "INVALID_PARAMETERS",
                    "errorMessage", "Parameter 'city' is required",
                    "correlationId", "mocked-corr"
            );

            when(adapterRegistry.hasAdapter("branchFinder")).thenReturn(true);
            when(adapterRegistry.invoke(eq("branchFinder"), anyMap(), anyString()))
                    .thenReturn(adapterResponse);

            ToolInvokeRequest request = new ToolInvokeRequest(
                    "branchFinder", Map.of("limit", "5"));

            mockMvc.perform(post(INVOKE_URI)
                            .header(AUTH_HEADER, AUTH_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("INVALID_PARAMETERS"));
        }

        @Test
        @DisplayName("returns 400 for malformed JSON")
        void returns400ForMalformedJson() throws Exception {
            mockMvc.perform(post(INVOKE_URI)
                            .header(AUTH_HEADER, AUTH_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{not valid json}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Response PEP — PII masking
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("Response PEP — field masking")
    class ResponsePep {

        @Test
        @DisplayName("accountNumber fields are masked in response")
        void accountNumberIsMasked() throws Exception {
            Map<String, Object> adapterResponse = Map.of(
                    "success", true,
                    "data", Map.of(
                            "city", "Hamburg",
                            "totalMatches", 1,
                            "count", 1,
                            "branches", List.of(
                                    Map.of("branchId", "HAM-001", "name", "Hamburg Jungfernstieg",
                                            "accountNumber", "DE89 3704 0044 0532 0150 00")
                            )
                    ),
                    "correlationId", "mocked-corr"
            );

            when(adapterRegistry.hasAdapter("branchFinder")).thenReturn(true);
            when(adapterRegistry.invoke(eq("branchFinder"), anyMap(), anyString()))
                    .thenReturn(adapterResponse);

            ToolInvokeRequest request = new ToolInvokeRequest(
                    "branchFinder", Map.of("city", "Hamburg"));

            mockMvc.perform(post(INVOKE_URI)
                            .header(AUTH_HEADER, AUTH_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.branches[0].accountNumber",
                            startsWith("****")));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Adapter unreachable (502)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /api/v1/tools/invoke — adapter unreachable")
    class AdapterUnreachable {

        @Test
        @DisplayName("returns 502 when adapter service is down")
        void returns502WhenAdapterDown() throws Exception {
            when(adapterRegistry.hasAdapter("branchFinder")).thenReturn(true);
            when(adapterRegistry.invoke(eq("branchFinder"), anyMap(), anyString()))
                    .thenThrow(new AdapterRegistry.AdapterInvocationException(
                            "branchFinder", "Adapter unreachable at http://localhost:8082/actions/search"));

            ToolInvokeRequest request = new ToolInvokeRequest(
                    "branchFinder", Map.of("city", "Frankfurt"));

            mockMvc.perform(post(INVOKE_URI)
                            .header(AUTH_HEADER, AUTH_TOKEN)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.error.code").value("ADAPTER_UNREACHABLE"));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Discovery endpoint
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /api/v1/tools — discovery")
    class Discovery {

        @Test
        @DisplayName("lists registered tools and adapters")
        void listTools() throws Exception {
            when(adapterRegistry.registeredTools()).thenReturn(List.of("branchFinder"));
            when(adapterRegistry.registeredAdapters()).thenReturn(List.of("branch-finder"));

            mockMvc.perform(get("/api/v1/tools")
                            .header(AUTH_HEADER, AUTH_TOKEN)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.count", greaterThanOrEqualTo(1)))
                    .andExpect(jsonPath("$.tools", hasItem("branchFinder")))
                    .andExpect(jsonPath("$.adapters", hasItem("branch-finder")));
        }
    }
}
