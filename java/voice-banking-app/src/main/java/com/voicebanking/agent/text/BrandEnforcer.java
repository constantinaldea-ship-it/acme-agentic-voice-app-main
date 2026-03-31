package com.voicebanking.agent.text;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Enforces brand guidelines by checking for prohibited phrases.
 * Ensures all responses comply with Acme Bank communication standards.
 */
@Component
public class BrandEnforcer {

    private static final Set<String> PROHIBITED_PHRASES = Set.of(
        "kostenlos",
        "gratis",
        "garantiert",
        "versprochen",
        "ohne risiko",
        "risikofrei",
        "sicher gewinnen",
        "konkurrenz",
        "andere banken"
    );

    public BrandCheckResult check(String text) {
        if (text == null || text.isBlank()) {
            return new BrandCheckResult(true, List.of());
        }

        String lowerText = text.toLowerCase();
        List<String> violations = new ArrayList<>();

        for (String phrase : PROHIBITED_PHRASES) {
            if (lowerText.contains(phrase)) {
                violations.add("Prohibited phrase: " + phrase);
            }
        }

        return new BrandCheckResult(violations.isEmpty(), violations);
    }

    public record BrandCheckResult(
        boolean compliant,
        List<String> violations
    ) {}
}
