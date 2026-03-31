package com.voicebanking.agent.mobileapp.domain;

/**
 * Categories of mobile app features.
 * 
 * Used to organize and filter app features for discovery and guidance.
 * Categories aligned with Acme Bank mobile app structure.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum FeatureCategory {
    SECURITY("Security", "Authentication and security settings", 
            new String[]{"Touch ID", "Face ID", "PIN", "password", "biometric", "login", "logout", "lock", "fingerprint"}),
    
    TRANSFERS("Transfers", "Money transfers and payments",
            new String[]{"transfer", "send money", "SEPA", "standing order", "payment", "recipient"}),
    
    ACCOUNTS("Accounts", "Account information and statements",
            new String[]{"balance", "statement", "account", "IBAN", "account number", "transaction history"}),
    
    CARDS("Cards", "Card management and controls",
            new String[]{"card", "credit card", "debit card", "block card", "unblock", "PIN", "card limit", "contactless"}),
    
    SETTINGS("Settings", "App configuration and preferences",
            new String[]{"settings", "notification", "preferences", "language", "profile", "alerts", "push"}),
    
    SUPPORT("Support", "Help and customer support",
            new String[]{"help", "support", "contact", "FAQ", "chat", "call", "feedback"});

    private final String displayName;
    private final String description;
    private final String[] keywords;

    FeatureCategory(String displayName, String description, String[] keywords) {
        this.displayName = displayName;
        this.description = description;
        this.keywords = keywords;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String[] getKeywords() {
        return keywords.clone();
    }

    /**
     * Find category that best matches the given query.
     * Returns null if no good match found.
     */
    public static FeatureCategory fromQuery(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String lower = query.toLowerCase();
        for (FeatureCategory category : values()) {
            for (String keyword : category.keywords) {
                if (lower.contains(keyword.toLowerCase())) {
                    return category;
                }
            }
        }
        return null;
    }
}
