package com.voicebanking.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Policy Gate Service
 * 
 * <p>Enforces safety policies before tool execution:</p>
 * <ul>
 *   <li>Tool allowlist (only read-only operations)</li>
 *   <li>Blocked intent detection (transfers, payments)</li>
 *   <li>Consent verification</li>
 * </ul>
 */
@Service
public class PolicyGateService {
    
    private static final Logger log = LoggerFactory.getLogger(PolicyGateService.class);
    
    private static final Set<String> ALLOWED_TOOLS = Set.of(
        "getBalance",
        "listAccounts",
        "queryTransactions",
        "summarizeTransactions"
    );
    
    private static final Set<String> BLOCKED_INTENTS = Set.of(
        "transfer",
        "payment",
        "send_money",
        "delete_account",
        "update_account",
        "create_account"
    );
    
    /**
     * Policy Evaluation Result
     */
    public record PolicyResult(
        boolean allowed,
        String reason
    ) {
        public static PolicyResult allow() {
            return new PolicyResult(true, null);
        }
        
        public static PolicyResult deny(String reason) {
            return new PolicyResult(false, reason);
        }
    }
    
    /**
     * Evaluate if a tool call should be allowed
     * 
     * @param toolName Tool name
     * @param consentAccepted Whether user accepted consent
     * @return Policy result
     */
    public PolicyResult evaluateToolCall(String toolName, boolean consentAccepted) {
        log.debug("Evaluating policy for tool: {}, consent: {}", toolName, consentAccepted);
        
        // Check consent (not strictly enforced for local stub, but logged)
        if (!consentAccepted) {
            log.warn("Tool call without consent (allowed in local mode): {}", toolName);
        }
        
        // Check if tool is in allowlist
        if (!ALLOWED_TOOLS.contains(toolName)) {
            String reason = "Tool '" + toolName + "' is not in the allowed list. " +
                "This demo only supports read-only banking operations.";
            log.warn("POLICY VIOLATION: Blocked tool call: {}", toolName);
            return PolicyResult.deny(reason);
        }
        
        log.info("Policy check passed for tool: {}", toolName);
        return PolicyResult.allow();
    }
    
    /**
     * Check if an intent is blocked
     * 
     * @param intent Intent name
     * @return true if blocked, false if allowed
     */
    public boolean isIntentBlocked(String intent) {
        return intent != null && BLOCKED_INTENTS.contains(intent.toLowerCase());
    }
    
    /**
     * Get refusal message for blocked intent
     */
    public String getRefusalMessage(String intent) {
        return "I cannot help with " + intent + " operations. " +
            "This demo is read-only and only supports balance inquiries and transaction queries.";
    }
}
