package com.voicebanking.agent.policy.domain;

public enum PolicyCategory {
    ALLOWED("Allowed", "Request is within scope and can be processed", true),
    MONEY_MOVEMENT("Money Movement", "Financial transactions require app", false),
    ADVISORY("Advisory", "Personalized advice requires advisor", false),
    TRADING("Trading", "Trading operations require platform", false),
    ACCOUNT_CHANGES("Account Changes", "Account changes require app", false),
    DISPUTES("Disputes", "Disputes require human review", false),
    NON_BANKING("Non-Banking", "Request is outside banking scope", false),
    HARMFUL("Harmful", "Inappropriate content", false),
    SECURITY_VIOLATION("Security Violation", "Security requests blocked", false);

    private final String displayName;
    private final String description;
    private final boolean allowed;

    PolicyCategory(String displayName, String description, boolean allowed) {
        this.displayName = displayName;
        this.description = description;
        this.allowed = allowed;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public boolean isAllowed() { return allowed; }
    public boolean requiresHandover() { return this == DISPUTES; }
    public boolean isSecuritySensitive() { return this == SECURITY_VIOLATION || this == HARMFUL; }
}
