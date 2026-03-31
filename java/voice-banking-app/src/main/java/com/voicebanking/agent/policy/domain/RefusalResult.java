package com.voicebanking.agent.policy.domain;

import java.util.List;

public record RefusalResult(
    PolicyCategory category,
    String message,
    String textMessage,
    List<String> alternatives,
    boolean handoverOffered,
    String templateId
) {
    public RefusalResult {
        if (textMessage == null || textMessage.isBlank()) textMessage = message;
        if (alternatives == null) alternatives = List.of();
    }

    public static RefusalResult of(PolicyCategory category, String message, List<String> alternatives, String templateId) {
        return new RefusalResult(category, message, message, alternatives, category.requiresHandover(), templateId);
    }

    public static RefusalResult withHandover(PolicyCategory category, String message, List<String> alternatives, String templateId) {
        return new RefusalResult(category, message, message, alternatives, true, templateId);
    }

    public static RefusalResult securityRefusal(String message, String templateId) {
        return new RefusalResult(PolicyCategory.SECURITY_VIOLATION, message, message,
            List.of("If you need help with account security, please call our customer service."), false, templateId);
    }

    public String getVoiceMessage() { return message; }
    public boolean hasAlternatives() { return alternatives != null && !alternatives.isEmpty(); }
}
