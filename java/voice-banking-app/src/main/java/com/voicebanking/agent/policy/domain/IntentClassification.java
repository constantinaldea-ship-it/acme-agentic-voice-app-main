package com.voicebanking.agent.policy.domain;

import java.time.Instant;

public record IntentClassification(
    String intent,
    PolicyCategory category,
    double confidence,
    String matchedRuleId,
    MatchType matchedBy,
    Instant timestamp
) {
    public enum MatchType { INTENT, KEYWORD, DEFAULT, SECURITY_KEYWORD }

    public IntentClassification {
        if (timestamp == null) timestamp = Instant.now();
        if (confidence < 0.0) confidence = 0.0;
        if (confidence > 1.0) confidence = 1.0;
    }

    /** Alias for intent for clarity in logging */
    public String matchedPattern() { return intent; }
    
    /** Alias for matchedBy for logging service */
    public MatchType matchType() { return matchedBy; }

    public static IntentClassification fromIntentMatch(String intent, PolicyCategory category, String ruleId, double confidence) {
        return new IntentClassification(intent, category, confidence, ruleId, MatchType.INTENT, Instant.now());
    }

    public static IntentClassification fromKeywordMatch(String intent, PolicyCategory category, String ruleId, double confidence) {
        return new IntentClassification(intent, category, confidence, ruleId, MatchType.KEYWORD, Instant.now());
    }

    public static IntentClassification securityViolation(String intent, String ruleId) {
        return new IntentClassification(intent, PolicyCategory.SECURITY_VIOLATION, 1.0, ruleId, MatchType.SECURITY_KEYWORD, Instant.now());
    }

    public static IntentClassification defaultClassification(String intent) {
        return new IntentClassification(intent, PolicyCategory.ALLOWED, 0.5, null, MatchType.DEFAULT, Instant.now());
    }

    // Simplified factory methods for tests (category, confidence, pattern)
    public static IntentClassification fromIntentMatch(PolicyCategory category, double confidence, String pattern) {
        return new IntentClassification(pattern, category, confidence, null, MatchType.INTENT, Instant.now());
    }

    public static IntentClassification fromSecurityKeyword(PolicyCategory category, double confidence, String keyword) {
        return new IntentClassification(keyword, category, confidence, null, MatchType.SECURITY_KEYWORD, Instant.now());
    }

    public boolean isHighConfidence() { return confidence >= 0.85; }
    public boolean isSecurityViolation() { return category == PolicyCategory.SECURITY_VIOLATION; }
}
