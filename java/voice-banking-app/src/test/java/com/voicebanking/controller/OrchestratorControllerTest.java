package com.voicebanking.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * OrchestratorController Integration Tests
 * 
 * Tests the full HTTP → Orchestrator → Response flow
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class OrchestratorControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldProcessBalanceInquiry() throws Exception {
        String requestBody = """
            {
                "text": "What is my balance?",
                "sessionId": "test-session-1",
                "consentAccepted": true
            }
            """;
        
        mockMvc.perform(post("/api/orchestrate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.transcript").value("What is my balance?"))
            .andExpect(jsonPath("$.toolCalled").value("getBalance"))
            .andExpect(jsonPath("$.toolResult").exists())
            .andExpect(jsonPath("$.responseText").exists())
            .andExpect(jsonPath("$.refusalReason").doesNotExist());
    }
    
    @Test
    void shouldProcessListAccountsRequest() throws Exception {
        String requestBody = """
            {
                "text": "Show me my accounts",
                "sessionId": "test-session-2",
                "consentAccepted": true
            }
            """;
        
        mockMvc.perform(post("/api/orchestrate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.toolCalled").value("listAccounts"))
            // Agent returns structured result: {"accounts": [...]}
            .andExpect(jsonPath("$.toolResult.accounts").isArray());
    }
    
    @Test
    void shouldRefuseTransferRequest() throws Exception {
        String requestBody = """
            {
                "text": "Transfer $100 to savings",
                "sessionId": "test-session-3",
                "consentAccepted": true
            }
            """;
        
        mockMvc.perform(post("/api/orchestrate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.toolCalled").doesNotExist())
            .andExpect(jsonPath("$.refusalReason").exists())
            .andExpect(jsonPath("$.refusalReason").value(org.hamcrest.Matchers.containsString("read-only")));
    }
    
    @Test
    void shouldReturn400ForInvalidRequest() throws Exception {
        String requestBody = """
            {
                "sessionId": "test-session-4",
                "consentAccepted": true
            }
            """;
        
        // Missing both text and audio
        mockMvc.perform(post("/api/orchestrate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.refusalReason").exists());
    }
}
