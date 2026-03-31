package com.voicebanking.agent.mobileapp.domain;

/**
 * Types of issues that can be troubleshot by the MobileAppAssistanceAgent.
 * 
 * Used to categorize and route troubleshooting queries to appropriate solutions.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum IssueType {
    LOGIN("Login Issues", "Problems logging into the app",
            new String[]{"login", "password", "PIN", "locked out", "can't log in", "access", "forgot"}),
    
    PERFORMANCE("Performance Issues", "App speed or stability problems",
            new String[]{"slow", "crash", "freeze", "stuck", "loading", "performance", "lag", "unresponsive"}),
    
    FEATURE("Feature Issues", "Specific feature not working",
            new String[]{"not working", "doesn't work", "broken", "missing", "can't find", "error", "failed"}),
    
    SECURITY("Security Issues", "Biometric or security feature problems",
            new String[]{"Touch ID", "Face ID", "fingerprint", "biometric", "face recognition", "secure"}),
    
    UPDATE("Update Issues", "App update or version problems",
            new String[]{"update", "version", "upgrade", "install", "download", "app store", "play store"}),
    
    NETWORK("Network Issues", "Connection or sync problems",
            new String[]{"connection", "network", "offline", "sync", "internet", "wifi", "cellular", "timeout"}),
    
    NOTIFICATION("Notification Issues", "Push notification problems",
            new String[]{"notification", "alert", "push", "message", "reminder", "silent"});

    private final String displayName;
    private final String description;
    private final String[] keywords;

    IssueType(String displayName, String description, String[] keywords) {
        this.displayName = displayName;
        this.description = description;
        this.keywords = keywords;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public String[] getKeywords() { return keywords.clone(); }

    /**
     * Detect issue type from user description.
     * Returns FEATURE as default if no specific match found.
     */
    public static IssueType fromDescription(String description) {
        if (description == null || description.isBlank()) {
            return FEATURE;
        }
        String lower = description.toLowerCase();
        
        // Check each type in priority order
        for (IssueType type : values()) {
            for (String keyword : type.keywords) {
                if (lower.contains(keyword.toLowerCase())) {
                    return type;
                }
            }
        }
        return FEATURE;
    }
}
