package com.voicebanking.service;

import com.voicebanking.domain.dto.OrchestratorRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

/**
 * OrchestratorService Unit Tests
 * 
 * Tests the end-to-end orchestration flow with golden utterances.
 */
@SpringBootTest
@ActiveProfiles("local")
class OrchestratorServiceTest {
    
    @Autowired
    private OrchestratorService orchestratorService;
    
    @ParameterizedTest
    @CsvSource({
        "'What is my balance?', getBalance",
        "'Show me my accounts', listAccounts",
        "'What are my recent transactions?', queryTransactions",
        "'How much did I spend at Starbucks?', queryTransactions"
    })
    void shouldProcessGoldenUtterances(String utterance, String expectedTool) {
        var request = new OrchestratorRequest(
            null,
            utterance,
            "test-session",
            true
        );
        
        var response = orchestratorService.process(request);
        
        assertThat(response.transcript()).isEqualTo(utterance);
        assertThat(response.toolCalled()).isEqualTo(expectedTool);
        assertThat(response.toolResult()).isNotNull();
        assertThat(response.refusalReason()).isNull();
    }
    
    @ParameterizedTest
    @CsvSource({
        "'Transfer $100 to savings'",
        "'Send money to John'",
        "'Pay my credit card'"
    })
    void shouldRefuseBlockedIntents(String utterance) {
        var request = new OrchestratorRequest(
            null,
            utterance,
            "test-session",
            true
        );
        
        var response = orchestratorService.process(request);
        
        assertThat(response.transcript()).isEqualTo(utterance);
        assertThat(response.toolCalled()).isNull();
        assertThat(response.refusalReason()).isNotNull();
        assertThat(response.refusalReason()).contains("read-only");
    }
    
    @Test
    void shouldRefuseOutOfScopeRequest() {
        var request = new OrchestratorRequest(
            null,
            "What is the weather today?",
            "test-session",
            true
        );
        
        var response = orchestratorService.process(request);
        
        assertThat(response.refusalReason()).isNotNull();
        assertThat(response.refusalReason()).contains("banking inquiries");
    }
    
    @Test
    void shouldHandleInvalidRequest() {
        var request = new OrchestratorRequest(
            null,  // No audio
            null,  // No text - invalid!
            "test-session",
            true
        );
        
        var response = orchestratorService.process(request);
        
        assertThat(response.refusalReason()).isNotNull();
        assertThat(response.refusalReason()).contains("either audio or text");
    }
}
