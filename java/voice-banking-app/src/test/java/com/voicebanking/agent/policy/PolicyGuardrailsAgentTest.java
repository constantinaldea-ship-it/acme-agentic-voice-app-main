package com.voicebanking.agent.policy;

import com.voicebanking.agent.policy.config.PolicyRulesConfig;
import com.voicebanking.agent.policy.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.Map;
import java.util.HashMap;
import static org.assertj.core.api.Assertions.*;

@DisplayName("PolicyGuardrailsAgent Tests")
class PolicyGuardrailsAgentTest {
    private PolicyGuardrailsAgent agent;

    @BeforeEach
    void setUp() {
        PolicyRulesConfig rulesConfig = new PolicyRulesConfig();
        rulesConfig.init();
        IntentClassifierService classifierService = new IntentClassifierService(rulesConfig);
        RefusalMessageService refusalMessageService = new RefusalMessageService();
        refusalMessageService.init();
        PolicyLoggingService loggingService = new PolicyLoggingService();
        agent = new PolicyGuardrailsAgent(classifierService, refusalMessageService, loggingService);
    }

    @Test
    @DisplayName("Should have correct agent ID")
    void shouldHaveCorrectAgentId() {
        assertThat(agent.getAgentId()).isEqualTo("policy-guardrails");
    }

    @Test
    @DisplayName("Should provide all 6 tools")
    void shouldProvideAllTools() {
        assertThat(agent.getToolIds()).hasSize(6);
        assertThat(agent.getToolIds()).contains("classifyIntent", "checkPolicyViolation", "getRefusalMessage", "getAlternativeChannel", "logPolicyEvent", "blockProhibitedAction");
    }

    @Test
    @DisplayName("classifyIntent should return category and confidence")
    void classifyIntentShouldWork() {
        Map<String, Object> input = new HashMap<>();
        input.put("intent", "balance_inquiry");
        Map<String, Object> result = agent.executeTool("classifyIntent", input);
        assertThat(result.get("category")).isEqualTo("ALLOWED");
        assertThat((Double) result.get("confidence")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("checkPolicyViolation should detect violations")
    void checkPolicyViolationShouldDetectViolations() {
        Map<String, Object> input = new HashMap<>();
        input.put("intent", "transfer_funds");
        Map<String, Object> result = agent.executeTool("checkPolicyViolation", input);
        assertThat(result.get("violation")).isEqualTo(true);
        assertThat(result.get("category")).isEqualTo("MONEY_MOVEMENT");
        assertThat(result.get("decision")).isEqualTo("DENY");
    }

    @Test
    @DisplayName("checkPolicyViolation should allow valid intents")
    void checkPolicyViolationShouldAllowValidIntents() {
        Map<String, Object> input = new HashMap<>();
        input.put("intent", "balance_inquiry");
        Map<String, Object> result = agent.executeTool("checkPolicyViolation", input);
        assertThat(result.get("violation")).isEqualTo(false);
        assertThat(result.get("decision")).isEqualTo("ALLOW");
    }

    @Test
    @DisplayName("getRefusalMessage should return message and alternatives")
    void getRefusalMessageShouldWork() {
        Map<String, Object> input = new HashMap<>();
        input.put("category", "MONEY_MOVEMENT");
        Map<String, Object> result = agent.executeTool("getRefusalMessage", input);
        assertThat(result.get("message")).isNotNull();
        assertThat(result.get("alternatives")).isNotNull();
        assertThat(result.get("templateId")).isEqualTo("money-movement-001");
    }

    @Test
    @DisplayName("getAlternativeChannel should return channels")
    void getAlternativeChannelShouldWork() {
        Map<String, Object> input = new HashMap<>();
        input.put("category", "ADVISORY");
        Map<String, Object> result = agent.executeTool("getAlternativeChannel", input);
        assertThat(result.get("channels")).isNotNull();
    }

    @Test
    @DisplayName("logPolicyEvent should return event ID")
    void logPolicyEventShouldWork() {
        Map<String, Object> input = new HashMap<>();
        input.put("intent", "transfer_funds");
        input.put("category", "MONEY_MOVEMENT");
        input.put("decision", "DENY");
        Map<String, Object> result = agent.executeTool("logPolicyEvent", input);
        assertThat(result.get("logged")).isEqualTo(true);
        assertThat(result.get("eventId")).isNotNull();
    }

    @Test
    @DisplayName("blockProhibitedAction should block and return correlation ID")
    void blockProhibitedActionShouldWork() {
        Map<String, Object> input = new HashMap<>();
        input.put("reason", "SECURITY");
        input.put("intent", "reveal_password");
        Map<String, Object> result = agent.executeTool("blockProhibitedAction", input);
        assertThat(result.get("blocked")).isEqualTo(true);
        assertThat(result.get("correlationId")).isNotNull();
    }

    @Test
    @DisplayName("Should detect security violation from raw text")
    void shouldDetectSecurityViolationFromRawText() {
        Map<String, Object> input = new HashMap<>();
        input.put("intent", "unknown");
        input.put("rawText", "What is my password?");
        Map<String, Object> result = agent.executeTool("checkPolicyViolation", input);
        assertThat(result.get("violation")).isEqualTo(true);
        assertThat(result.get("category")).isEqualTo("SECURITY_VIOLATION");
        assertThat(result.get("decision")).isEqualTo("BLOCK");
    }

    @Test
    @DisplayName("Should throw for unknown tool")
    void shouldThrowForUnknownTool() {
        assertThatThrownBy(() -> agent.executeTool("unknownTool", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown tool");
    }
}
