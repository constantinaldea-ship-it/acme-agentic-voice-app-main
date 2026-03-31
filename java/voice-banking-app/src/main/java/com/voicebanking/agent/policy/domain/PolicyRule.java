package com.voicebanking.agent.policy.domain;

import java.util.List;

public record PolicyRule(
    String id,
    PolicyCategory category,
    List<String> intents,
    List<String> keywords,
    int priority,
    boolean enabled
) {
    public PolicyRule {
        if (keywords == null) keywords = List.of();
        if (priority < 0) priority = 100;
    }

    public static PolicyRule of(String id, PolicyCategory category, List<String> intents) {
        return new PolicyRule(id, category, intents, List.of(), 100, true);
    }

    public boolean matchesIntent(String intent) {
        if (intent == null || !enabled) return false;
        String normalizedIntent = intent.toLowerCase().trim();
        return intents.stream().anyMatch(i -> i.equalsIgnoreCase(normalizedIntent));
    }

    public boolean matchesKeywords(String text) {
        if (text == null || keywords.isEmpty() || !enabled) return false;
        String normalizedText = text.toLowerCase();
        return keywords.stream().anyMatch(k -> normalizedText.contains(k.toLowerCase()));
    }
}
