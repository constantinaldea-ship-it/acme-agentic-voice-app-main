package com.voicebanking.agent.text.domain;

/**
 * Response Type Enumeration
 * 
 * Categorizes the types of responses that can be generated.
 * Each type has specific formatting rules and template associations.
 * 
 * @author Augment Agent
 * @since 2026-01-22
 */
public enum ResponseType {
    
    BALANCE("balance", "Account balance information"),
    TRANSACTION("transaction", "Transaction details"),
    CONFIRMATION("confirmation", "Action confirmation"),
    ERROR("error", "Error explanation"),
    CLARIFICATION("clarification", "Request for clarification"),
    INFORMATION("information", "General information"),
    GREETING("greeting", "Greeting message"),
    CLOSING("closing", "Closing message"),
    PRODUCT_INFO("product_info", "Product information");
    
    private final String templatePrefix;
    private final String description;
    
    ResponseType(String templatePrefix, String description) {
        this.templatePrefix = templatePrefix;
        this.description = description;
    }
    
    public String getTemplatePrefix() { return templatePrefix; }
    public String getDescription() { return description; }
    
    public static ResponseType fromString(String value) {
        if (value == null || value.isBlank()) return INFORMATION;
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            for (ResponseType type : values()) {
                if (type.templatePrefix.equalsIgnoreCase(value)) return type;
            }
            return INFORMATION;
        }
    }
}
