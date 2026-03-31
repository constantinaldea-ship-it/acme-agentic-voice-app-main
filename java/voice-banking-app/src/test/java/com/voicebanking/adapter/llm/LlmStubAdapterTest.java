package com.voicebanking.adapter.llm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * LlmStubAdapter Unit Tests
 */
@SpringBootTest
@ActiveProfiles("local")
class LlmStubAdapterTest {
    
    @Autowired
    private LlmProvider llmProvider;
    
    @ParameterizedTest
    @CsvSource({
        "'What is my balance?', getBalance",
        "'Show me my accounts', listAccounts",
        "'What are my recent transactions?', queryTransactions"
    })
    void shouldDetectIntentForBalanceInquiries(String transcript, String expectedTool) {
        var response = llmProvider.process(transcript, Map.of());
        
        assertThat(response.type()).isEqualTo(LlmResponse.ResponseType.TOOL_CALL);
        assertThat(response.toolCall()).isNotNull();
        assertThat(response.toolCall().toolName()).isEqualTo(expectedTool);
    }
    
    @ParameterizedTest
    @CsvSource({
        "'Transfer $100 to savings'",
        "'Send money to John'"
    })
    void shouldRefuseBlockedIntents(String transcript) {
        var response = llmProvider.process(transcript, Map.of());
        
        assertThat(response.type()).isEqualTo(LlmResponse.ResponseType.REFUSAL);
        assertThat(response.message()).contains("read-only");
    }
    
    @Test
    void shouldRefuseOutOfScope() {
        var response = llmProvider.process("What is the weather?", Map.of());
        
        assertThat(response.type()).isEqualTo(LlmResponse.ResponseType.REFUSAL);
        assertThat(response.message()).contains("banking inquiries");
    }
    
    @Test
    void shouldClarifyAmbiguous() {
        var response = llmProvider.process("I need help", Map.of());
        
        assertThat(response.type()).isEqualTo(LlmResponse.ResponseType.CLARIFICATION);
        assertThat(response.message()).containsIgnoringCase("not sure");
    }
}
