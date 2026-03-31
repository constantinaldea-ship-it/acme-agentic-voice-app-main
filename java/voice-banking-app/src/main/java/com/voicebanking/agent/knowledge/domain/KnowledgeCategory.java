package com.voicebanking.agent.knowledge.domain;

/**
 * Knowledge Category Enumeration
 *
 * @author Augment Agent
 * @since 2026-01-22
 */
public enum KnowledgeCategory {
    GENERAL_INFO("General Information", "Bank facts, BIC codes, contact information"),
    PRODUCT_INFO("Product Information", "Product features, fees, and terms"),
    HOW_TO("How-To Guides", "Step-by-step instructions for banking tasks"),
    FAQ("FAQ", "Common questions and answers"),
    BRANCH_INFO("Branch Information", "Branch hours, services, and locations"),
    APP_GUIDANCE("App Guidance", "Mobile app navigation and feature guides");

    private final String displayName;
    private final String description;

    KnowledgeCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
}
