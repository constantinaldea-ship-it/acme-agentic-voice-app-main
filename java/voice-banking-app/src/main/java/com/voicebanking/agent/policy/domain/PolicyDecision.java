package com.voicebanking.agent.policy.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PolicyDecision(
    Decision decision,
    PolicyCategory category,
    String message,
    List<String> alternatives,
    String correlationId,
    Instant timestamp,
    String intent,
    double confidence
) {
    public enum Decision { ALLOW, DENY, BLOCK, ESCALATE }

    public PolicyDecision {
        if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
        if (timestamp == null) timestamp = Instant.now();
        if (alternatives == null) alternatives = List.of();
        if (confidence < 0.0 || confidence > 1.0) confidence = 1.0;
    }

    // Factory methods using intent string
    public static PolicyDecision allow(String intent, PolicyCategory category) {
        return new PolicyDecision(Decision.ALLOW, category, "Request allowed", List.of(),
            UUID.randomUUID().toString(), Instant.now(), intent, 1.0);
    }

    public static PolicyDecision deny(String intent, PolicyCategory category, String message, List<String> alternatives) {
        return new PolicyDecision(Decision.DENY, category, message, alternatives,
            UUID.randomUUID().toString(), Instant.now(), intent, 1.0);
    }

    public static PolicyDecision block(String intent, PolicyCategory category, String message) {
        return new PolicyDecision(Decision.BLOCK, category, message, List.of(),
            UUID.randomUUID().toString(), Instant.now(), intent, 1.0);
    }

    public static PolicyDecision escalate(String intent, PolicyCategory category, String message, List<String> alternatives) {
        return new PolicyDecision(Decision.ESCALATE, category, message, alternatives,
            UUID.randomUUID().toString(), Instant.now(), intent, 1.0);
    }

    // Factory methods using IntentClassification
    public static PolicyDecision allow(PolicyCategory category, String message, IntentClassification classification) {
        return new PolicyDecision(Decision.ALLOW, category, message, List.of(),
            UUID.randomUUID().toString(), Instant.now(), 
            classification != null ? classification.matchedPattern() : null,
            classification != null ? classification.confidence() : 1.0);
    }

    public static PolicyDecision deny(PolicyCategory category, String message, IntentClassification classification) {
        return new PolicyDecision(Decision.DENY, category, message, List.of(),
            UUID.randomUUID().toString(), Instant.now(),
            classification != null ? classification.matchedPattern() : null,
            classification != null ? classification.confidence() : 1.0);
    }

    public static PolicyDecision block(PolicyCategory category, String message, IntentClassification classification) {
        return new PolicyDecision(Decision.BLOCK, category, message, List.of(),
            UUID.randomUUID().toString(), Instant.now(),
            classification != null ? classification.matchedPattern() : null,
            classification != null ? classification.confidence() : 1.0);
    }

    public static PolicyDecision escalate(PolicyCategory category, String message, IntentClassification classification) {
        return new PolicyDecision(Decision.ESCALATE, category, message, List.of(),
            UUID.randomUUID().toString(), Instant.now(),
            classification != null ? classification.matchedPattern() : null,
            classification != null ? classification.confidence() : 1.0);
    }

    public boolean isAllowed() { return decision == Decision.ALLOW; }
    public boolean isBlocked() { return decision == Decision.BLOCK; }
    public boolean requiresEscalation() { return decision == Decision.ESCALATE; }
}
