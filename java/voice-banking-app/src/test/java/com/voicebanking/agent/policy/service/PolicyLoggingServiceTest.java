package com.voicebanking.agent.policy.service;

import com.voicebanking.agent.policy.domain.IntentClassification;
import com.voicebanking.agent.policy.domain.PolicyCategory;
import com.voicebanking.agent.policy.domain.PolicyDecision;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.*;

@DisplayName("PolicyLoggingService Tests")
class PolicyLoggingServiceTest {
    private PolicyLoggingService loggingService;

    @BeforeEach
    void setUp() {
        loggingService = new PolicyLoggingService();
    }

    @Test
    @DisplayName("Should log policy decision and return event ID")
    void shouldLogPolicyDecision() {
        IntentClassification classification = IntentClassification.fromIntentMatch(
            PolicyCategory.MONEY_MOVEMENT, 1.0, "transfer_funds"
        );
        PolicyDecision decision = PolicyDecision.deny(
            PolicyCategory.MONEY_MOVEMENT,
            "Transfers not allowed via voice",
            classification
        );

        String eventId = loggingService.logPolicyDecision(decision, "REQ-123");
        
        assertThat(eventId).isNotNull();
        assertThat(eventId).startsWith("POL-");
    }

    @Test
    @DisplayName("Should log security event and return event ID")
    void shouldLogSecurityEvent() {
        String eventId = loggingService.logSecurityEvent(
            "BLOCK",
            "password",
            "REQ-456"
        );
        
        assertThat(eventId).isNotNull();
        assertThat(eventId).startsWith("SEC-");
    }

    @Test
    @DisplayName("Should log classification event")
    void shouldLogClassificationEvent() {
        IntentClassification classification = IntentClassification.fromIntentMatch(
            PolicyCategory.ALLOWED, 1.0, "balance_inquiry"
        );

        // Should not throw
        assertThatCode(() -> loggingService.logClassificationEvent(classification, "REQ-789"))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle null correlation ID")
    void shouldHandleNullCorrelationId() {
        IntentClassification classification = IntentClassification.fromIntentMatch(
            PolicyCategory.HARMFUL, 1.0, "harmful_content"
        );
        PolicyDecision decision = PolicyDecision.block(
            PolicyCategory.HARMFUL,
            "Harmful content blocked",
            classification
        );

        String eventId = loggingService.logPolicyDecision(decision, null);
        
        assertThat(eventId).isNotNull();
    }

    @Test
    @DisplayName("Should generate unique event IDs")
    void shouldGenerateUniqueEventIds() {
        IntentClassification classification = IntentClassification.fromIntentMatch(
            PolicyCategory.ALLOWED, 1.0, "balance_inquiry"
        );
        PolicyDecision decision = PolicyDecision.allow(
            PolicyCategory.ALLOWED,
            "Allowed",
            classification
        );

        String eventId1 = loggingService.logPolicyDecision(decision, "REQ-1");
        String eventId2 = loggingService.logPolicyDecision(decision, "REQ-2");
        
        assertThat(eventId1).isNotEqualTo(eventId2);
    }

    @Test
    @DisplayName("Should log ALLOW decisions")
    void shouldLogAllowDecisions() {
        IntentClassification classification = IntentClassification.fromIntentMatch(
            PolicyCategory.ALLOWED, 1.0, "account_info"
        );
        PolicyDecision decision = PolicyDecision.allow(
            PolicyCategory.ALLOWED,
            "Request allowed",
            classification
        );

        String eventId = loggingService.logPolicyDecision(decision, "REQ-ALLOW");
        
        assertThat(eventId).isNotNull();
    }

    @Test
    @DisplayName("Should log BLOCK decisions with audit trail")
    void shouldLogBlockDecisionsWithAuditTrail() {
        IntentClassification classification = IntentClassification.fromSecurityKeyword(
            PolicyCategory.SECURITY_VIOLATION, 1.0, "password"
        );
        PolicyDecision decision = PolicyDecision.block(
            PolicyCategory.SECURITY_VIOLATION,
            "Security violation blocked",
            classification
        );

        String eventId = loggingService.logPolicyDecision(decision, "REQ-SECURITY");
        
        assertThat(eventId).isNotNull();
        assertThat(eventId).startsWith("POL-");
    }

    @Test
    @DisplayName("Should log ESCALATE decisions")
    void shouldLogEscalateDecisions() {
        IntentClassification classification = IntentClassification.fromIntentMatch(
            PolicyCategory.ADVISORY, 1.0, "investment_advice"
        );
        PolicyDecision decision = PolicyDecision.escalate(
            PolicyCategory.ADVISORY,
            "Escalating to human advisor",
            classification
        );

        String eventId = loggingService.logPolicyDecision(decision, "REQ-ESCALATE");
        
        assertThat(eventId).isNotNull();
    }
}
