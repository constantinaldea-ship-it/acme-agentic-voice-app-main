package com.voicebanking.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * AccountController Integration Tests
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class AccountControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldReturnAllAccounts() throws Exception {
        mockMvc.perform(get("/api/accounts"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].type").exists());
    }
    
    @Test
    void shouldReturnBalanceForValidAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/acc-checking-001/balance"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.accountId").value("acc-checking-001"))
            .andExpect(jsonPath("$.available").exists())
            .andExpect(jsonPath("$.currency").value("USD"));
    }
    
    @Test
    void shouldReturn404ForNonExistentAccount() throws Exception {
        mockMvc.perform(get("/api/accounts/invalid/balance"))
            .andExpect(status().isNotFound());
    }
    
    @Test
    void shouldReturnTransactions() throws Exception {
        mockMvc.perform(get("/api/accounts/acc-checking-001/transactions?limit=5"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.length()").value(5))
            .andExpect(jsonPath("$[0].id").exists())
            .andExpect(jsonPath("$[0].description").exists());
    }
}
