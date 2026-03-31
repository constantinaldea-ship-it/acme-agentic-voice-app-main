package com.voicebanking.agent.mobileapp.domain;

/**
 * Mobile platform enumeration for platform-specific guidance.
 * 
 * Acme Bank mobile app is available on iOS and Android with some
 * platform-specific differences in navigation and features.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum Platform {
    IOS("iOS", "Apple iPhone/iPad"),
    ANDROID("Android", "Android devices"),
    BOTH("Both", "All platforms");

    private final String displayName;
    private final String description;

    Platform(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parse platform from user input string.
     * Handles common variations like "iPhone", "Samsung", etc.
     */
    public static Platform fromUserInput(String input) {
        if (input == null || input.isBlank()) {
            return BOTH;
        }
        String lower = input.toLowerCase().trim();
        if (lower.contains("ios") || lower.contains("iphone") || lower.contains("ipad") || lower.contains("apple")) {
            return IOS;
        }
        if (lower.contains("android") || lower.contains("samsung") || lower.contains("pixel") || lower.contains("galaxy")) {
            return ANDROID;
        }
        return BOTH;
    }
}
