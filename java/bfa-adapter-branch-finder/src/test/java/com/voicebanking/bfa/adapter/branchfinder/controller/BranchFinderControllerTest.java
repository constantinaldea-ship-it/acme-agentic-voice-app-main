package com.voicebanking.bfa.adapter.branchfinder.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebanking.bfa.adapter.branchfinder.dto.AdapterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link BranchFinderController}.
 *
 * @author Copilot
 * @since 2026-03-01
 */
@SpringBootTest
@AutoConfigureMockMvc
class BranchFinderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Happy Path
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /actions/search — happy path")
    class HappyPath {

        @Test
        @DisplayName("returns branches for known city")
        void invokeSuccess() throws Exception {
            AdapterRequest request = new AdapterRequest("corr-001",
                    Map.of("city", "Frankfurt"));

            mockMvc.perform(post("/actions/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.city").value("Frankfurt"))
                    .andExpect(jsonPath("$.data.totalMatches", greaterThan(0)))
                    .andExpect(jsonPath("$.data.branches").isArray())
                    .andExpect(jsonPath("$.data.branches[0].branchId").exists())
                    .andExpect(jsonPath("$.correlationId").value("corr-001"));
        }

        @Test
        @DisplayName("returns empty list for unknown city")
        void invokeUnknownCity() throws Exception {
            AdapterRequest request = new AdapterRequest("corr-002",
                    Map.of("city", "Atlantis"));

            mockMvc.perform(post("/actions/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalMatches").value(0))
                    .andExpect(jsonPath("$.data.branches").isEmpty());
        }

        @Test
        @DisplayName("respects limit parameter")
        void invokeWithLimit() throws Exception {
            AdapterRequest request = new AdapterRequest("corr-003",
                    Map.of("city", "Frankfurt", "limit", 1));

            mockMvc.perform(post("/actions/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.count").value(1));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Validation Errors
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("POST /actions/search — validation")
    class Validation {

        @Test
        @DisplayName("returns 400 for missing city")
        void returns400ForMissingCity() throws Exception {
            AdapterRequest request = new AdapterRequest("corr-err-001",
                    Map.of("limit", 5));

            mockMvc.perform(post("/actions/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETERS"));
        }

        @Test
        @DisplayName("returns 400 for null parameters")
        void returns400ForNullParameters() throws Exception {
            String body = """
                    {"correlationId": "corr-err-002"}
                    """;

            mockMvc.perform(post("/actions/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETERS"));
        }

        @Test
        @DisplayName("returns 400 for invalid limit")
        void returns400ForInvalidLimit() throws Exception {
            AdapterRequest request = new AdapterRequest("corr-err-003",
                    Map.of("city", "Berlin", "limit", "abc"));

            mockMvc.perform(post("/actions/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETERS"));
        }

        @Test
        @DisplayName("returns 400 for out-of-range limit")
        void returns400ForOutOfRangeLimit() throws Exception {
            AdapterRequest request = new AdapterRequest("corr-err-004",
                    Map.of("city", "Berlin", "limit", 0));

            mockMvc.perform(post("/actions/search")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.errorCode").value("INVALID_PARAMETERS"));
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Health
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Nested
    @DisplayName("GET /health")
    class Health {

        @Test
        @DisplayName("returns adapter health info")
        void healthCheck() throws Exception {
            mockMvc.perform(get("/health")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"))
                    .andExpect(jsonPath("$.adapter").value("branch-finder"));
        }
    }
}
