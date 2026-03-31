package com.voicebanking.agent.personalfinance.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * Policy enforcer to avoid advisory language in personal finance outputs.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class PolicyEnforcer {
    private static final Logger log = LoggerFactory.getLogger(PolicyEnforcer.class);

    private static final List<String> EN_FORBIDDEN = List.of(
            "should", "recommend", "consider", "you might", "you need to", "i suggest"
    );

    private static final List<String> DE_FORBIDDEN = List.of(
            "solltest", "empfehle", "empfehlung", "du solltest", "ich empfehle", "du solltest" 
    );

    public PolicyResult enforce(String text, String language) {
        if (text == null) {
            return new PolicyResult("", false, null);
        }

        String lower = text.toLowerCase(Locale.ROOT);
        boolean violation = containsForbidden(lower, language);

        if (!violation) {
            return new PolicyResult(text, false, null);
        }

        log.warn("Policy violation detected in PFM response");
        String sanitized = text.replaceAll("(?i)should|recommend|consider|you might|you need to|i suggest", "");
        sanitized = sanitized.replaceAll("(?i)solltest|empfehle|empfehlung|du solltest|ich empfehle", "");

        String disclaimer = language != null && language.toLowerCase(Locale.ROOT).startsWith("de")
                ? "Ich kann nur Fakten zu Ihren Ausgaben nennen. Für persönliche Beratung sprechen Sie bitte mit einer Finanzberaterin oder einem Finanzberater."
                : "I can provide factual spending insights only. For personal financial advice, please speak with a financial advisor.";

        return new PolicyResult(sanitized.trim(), true, disclaimer);
    }

    private boolean containsForbidden(String lower, String language) {
        List<String> forbidden = language != null && language.toLowerCase(Locale.ROOT).startsWith("de")
                ? DE_FORBIDDEN : EN_FORBIDDEN;
        return forbidden.stream().anyMatch(lower::contains);
    }

    public record PolicyResult(String text, boolean violated, String disclaimer) {}
}
