package com.voicebanking.agent.policy.service;

import com.voicebanking.agent.policy.domain.IntentClassification;
import com.voicebanking.agent.policy.domain.PolicyCategory;
import com.voicebanking.agent.policy.domain.PolicyDecision;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PolicyLoggingService {
    private static final Logger log = LoggerFactory.getLogger(PolicyLoggingService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT.POLICY");

    /**
     * Logs a policy decision with full audit trail.
     * @param decision The policy decision
     * @param correlationId Request correlation ID
     * @return Event ID for tracking
     */
    public String logPolicyDecision(PolicyDecision decision, String correlationId) {
        String eventId = "POL-" + UUID.randomUUID().toString().substring(0, 8);
        
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("eventType", "POLICY_DECISION");
        logEntry.put("eventId", eventId);
        logEntry.put("timestamp", Instant.now().toString());
        logEntry.put("correlationId", correlationId != null ? correlationId : "N/A");
        logEntry.put("agentId", "policy-guardrails");
        logEntry.put("intent", sanitize(decision.intent()));
        logEntry.put("category", decision.category().name());
        logEntry.put("decision", decision.decision().name());
        logEntry.put("confidence", decision.confidence());
        
        auditLog.info("PolicyDecision: {}", logEntry);
        
        if (decision.category().isSecuritySensitive()) {
            logSecurityEventInternal(decision.decision().name(), decision.intent(), correlationId, eventId);
        }
        return eventId;
    }

    /**
     * Logs a security event with separate tracking.
     * @param action The action taken (e.g., BLOCK)
     * @param trigger What triggered the event
     * @param correlationId Request correlation ID
     * @return Event ID for tracking
     */
    public String logSecurityEvent(String action, String trigger, String correlationId) {
        String eventId = "SEC-" + UUID.randomUUID().toString().substring(0, 8);
        logSecurityEventInternal(action, trigger, correlationId, eventId);
        return eventId;
    }

    private void logSecurityEventInternal(String action, String trigger, String correlationId, String eventId) {
        Map<String, Object> securityEntry = Map.of(
            "eventType", "SECURITY_VIOLATION_ATTEMPT",
            "eventId", eventId,
            "timestamp", Instant.now().toString(),
            "correlationId", correlationId != null ? correlationId : "N/A",
            "action", action,
            "trigger", sanitize(trigger),
            "blocked", true
        );
        auditLog.warn("SecurityViolation: {}", securityEntry);
        log.warn("Security violation detected and blocked: action={}, eventId={}", action, eventId);
    }

    /**
     * Logs an intent classification event.
     * @param classification The classification result
     * @param correlationId Request correlation ID
     */
    public void logClassificationEvent(IntentClassification classification, String correlationId) {
        log.debug("Intent classified: intent={}, category={}, matchType={}, confidence={}, correlationId={}", 
            sanitize(classification.matchedPattern()), 
            classification.category(), 
            classification.matchType(),
            classification.confidence(),
            correlationId);
    }

    private String sanitize(String input) {
        if (input == null) return "";
        String cleaned = input.replaceAll("[^a-zA-Z0-9_\\-\\s]", "");
        return cleaned.substring(0, Math.min(cleaned.length(), 100));
    }
}
