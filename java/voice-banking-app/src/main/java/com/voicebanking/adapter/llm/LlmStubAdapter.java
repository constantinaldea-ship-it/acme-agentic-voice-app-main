package com.voicebanking.adapter.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Stub LLM Adapter
 * 
 * <p>Deterministic LLM for local development and testing.
 * Maps golden utterances to predefined intent/tool calls.</p>
 * 
 * <p>Active in 'local' profile only.</p>
 */
@Component
@Profile("local")
public class LlmStubAdapter implements LlmProvider {
    
    private static final Logger log = LoggerFactory.getLogger(LlmStubAdapter.class);
    
    @Value("${voice-banking.stub.llm-latency-ms:100}")
    private long latencyMs;
    
    @Value("${voice-banking.stub.llm-confidence:0.90}")
    private double confidence;
    
    @Override
    public LlmResponse process(String transcript, Map<String, Object> context) {
        log.debug("LLM Stub: processing transcript '{}' (simulated latency: {}ms)", transcript, latencyMs);
        
        // Simulate latency
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Normalize transcript for matching
        String normalized = transcript.toLowerCase().trim();
        
        // Map golden utterances to responses
        LlmResponse response = matchUtterance(normalized);
        
        log.info("LLM Stub: detected intent type={}, toolCall={}", 
            response.type(), 
            response.toolCall() != null ? response.toolCall().toolName() : "none");
        
        return response;
    }
    
    private LlmResponse matchUtterance(String normalized) {
        // Transactions (check before balance because "spent" queries are about transactions)
        if (normalized.contains("transaction") || normalized.contains("spent") ||
            normalized.contains("recent") || normalized.contains("history") ||
            normalized.contains("starbucks") || normalized.contains("grocery") ||
            normalized.contains("amazon")) {
            return LlmResponse.toolCall(
                new ToolCall("queryTransactions", Map.of("limit", 10)),
                "Let me get your recent transactions."
            );
        }
        
        // Balance inquiries
        if (normalized.contains("balance") || normalized.contains("how much money")) {
            return LlmResponse.toolCall(
                new ToolCall("getBalance", Map.of()),
                "I'll check your balance for you."
            );
        }
        
        // List accounts
        if (normalized.contains("list") && normalized.contains("account") ||
            normalized.contains("show") && normalized.contains("account") ||
            normalized.contains("my accounts")) {
            return LlmResponse.toolCall(
                new ToolCall("listAccounts", Map.of()),
                "Here are your accounts."
            );
        }
        
        // Branch/ATM finder
        if (normalized.contains("branch") || normalized.contains("atm") ||
            normalized.contains("nearby") || normalized.contains("closest") ||
            normalized.contains("nearest") || normalized.contains("location")) {
            // Default to Frankfurt coordinates for stub
            return LlmResponse.toolCall(
                new ToolCall("findNearbyBranches", Map.of(
                    "latitude", 50.1109,
                    "longitude", 8.6821,
                    "radiusKm", 5.0,
                    "limit", 5,
                    "type", "all"
                )),
                "I'll find branches and ATMs near you."
            );
        }
        
        // Blocked intents: transfers
        if (normalized.contains("transfer") || normalized.contains("send money") ||
            normalized.contains("pay")) {
            return LlmResponse.refusal(
                "I can only provide read-only information. Transfers and payments are not available in this demo."
            );
        }
        
        // Out of scope
        if (normalized.contains("weather") || normalized.contains("news")) {
            return LlmResponse.refusal(
                "I can only help with banking inquiries like checking balances and viewing transactions."
            );
        }
        
        // Ambiguous or unclear requests
        if (normalized.contains("help") || normalized.contains("need") || 
            (!normalized.contains("account") && !normalized.contains("balance") && 
             !normalized.contains("transaction") && !normalized.contains("spent"))) {
            return LlmResponse.clarification(
                "I'm not sure what you'd like to do. You can ask about your balance, accounts, or recent transactions."
            );
        }
        
        // Default: clarification
        return LlmResponse.clarification(
            "I'm not sure what you'd like to do. You can ask about your balance, accounts, or recent transactions."
        );
    }
}
