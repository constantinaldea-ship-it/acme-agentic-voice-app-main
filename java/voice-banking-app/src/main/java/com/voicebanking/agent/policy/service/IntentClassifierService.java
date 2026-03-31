package com.voicebanking.agent.policy.service;

import com.voicebanking.agent.policy.config.PolicyRulesConfig;
import com.voicebanking.agent.policy.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class IntentClassifierService {
    private static final Logger log = LoggerFactory.getLogger(IntentClassifierService.class);
    private final PolicyRulesConfig rulesConfig;

    public IntentClassifierService(PolicyRulesConfig rulesConfig) {
        this.rulesConfig = rulesConfig;
    }

    public IntentClassification classify(String intent) {
        return classify(intent, null);
    }

    public IntentClassification classify(String intent, String rawText) {
        if (intent == null || intent.isBlank()) {
            log.warn("Empty intent received, returning default classification");
            return IntentClassification.defaultClassification(intent);
        }
        if (rawText != null && containsSecurityKeywords(rawText)) {
            log.warn("Security keywords detected in raw text");
            return IntentClassification.securityViolation(intent, "security-keywords");
        }
        Optional<PolicyRule> matchedRule = findMatchingRule(intent);
        if (matchedRule.isPresent()) {
            PolicyRule rule = matchedRule.get();
            log.debug("Intent {} matched rule {} with category {}", intent, rule.id(), rule.category());
            return IntentClassification.fromIntentMatch(intent, rule.category(), rule.id(), 1.0);
        }
        if (rawText != null) {
            Optional<PolicyRule> keywordMatch = findKeywordMatch(rawText);
            if (keywordMatch.isPresent()) {
                PolicyRule rule = keywordMatch.get();
                log.debug("Raw text matched keyword rule {} with category {}", rule.id(), rule.category());
                return IntentClassification.fromKeywordMatch(intent, rule.category(), rule.id(), 0.8);
            }
        }
        log.debug("No matching rule found for intent {}, returning default classification", intent);
        return IntentClassification.defaultClassification(intent);
    }

    private boolean containsSecurityKeywords(String text) {
        String lowerText = text.toLowerCase();
        return rulesConfig.getSecurityKeywords().stream().anyMatch(lowerText::contains);
    }

    private Optional<PolicyRule> findMatchingRule(String intent) {
        return rulesConfig.getAllRules().stream().filter(rule -> rule.matchesIntent(intent)).findFirst();
    }

    private Optional<PolicyRule> findKeywordMatch(String text) {
        return rulesConfig.getAllRules().stream().filter(rule -> rule.matchesKeywords(text)).findFirst();
    }

    public PolicyDecision evaluateIntent(String intent, String rawText) {
        IntentClassification classification = classify(intent, rawText);
        return createDecisionFromClassification(classification);
    }

    private PolicyDecision createDecisionFromClassification(IntentClassification classification) {
        PolicyCategory category = classification.category();
        String intent = classification.intent();
        if (category.isAllowed()) {
            return PolicyDecision.allow(intent, category);
        }
        if (category == PolicyCategory.SECURITY_VIOLATION) {
            return PolicyDecision.block(intent, category, "For your security, I cannot provide sensitive credentials.");
        }
        if (category == PolicyCategory.HARMFUL) {
            return PolicyDecision.block(intent, category, "I am here to help with your banking needs.");
        }
        if (category.requiresHandover()) {
            return PolicyDecision.escalate(intent, category, category.getDescription(), java.util.List.of("Connect with a specialist"));
        }
        return PolicyDecision.deny(intent, category, category.getDescription(), java.util.List.of("Use mobile app", "Visit online banking"));
    }
}
