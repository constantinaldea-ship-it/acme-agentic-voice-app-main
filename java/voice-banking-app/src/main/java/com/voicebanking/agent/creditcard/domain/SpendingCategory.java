package com.voicebanking.agent.creditcard.domain;

/**
 * Spending category classification for credit card transactions.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum SpendingCategory {
    RESTAURANTS("Restaurants", "Restaurants, Cafes, Fast Food"),
    GROCERIES("Groceries", "Supermarkets, Food Stores"),
    TRAVEL("Travel", "Airlines, Hotels, Travel Agencies"),
    TRANSPORT("Transport", "Gas, Parking, Public Transit"),
    SHOPPING("Shopping", "Retail, Online Shopping"),
    ENTERTAINMENT("Entertainment", "Streaming, Events, Gaming"),
    UTILITIES("Utilities", "Telecom, Energy, Internet"),
    HEALTH("Health", "Pharmacy, Medical, Fitness"),
    INSURANCE("Insurance", "Insurance Premiums"),
    EDUCATION("Education", "Schools, Courses, Books"),
    OTHER("Other", "Uncategorized");

    private final String displayName;
    private final String description;

    SpendingCategory(String displayName, String description) {
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
     * Categorize a merchant based on name or MCC code.
     * In production, this would use ML-based classification.
     */
    public static SpendingCategory categorize(String merchantName, String mccCode) {
        if (merchantName == null) {
            return OTHER;
        }
        String lowerName = merchantName.toLowerCase();
        
        if (lowerName.contains("restaurant") || lowerName.contains("cafe") || 
            lowerName.contains("starbucks") || lowerName.contains("mcdonald")) {
            return RESTAURANTS;
        }
        if (lowerName.contains("rewe") || lowerName.contains("edeka") || 
            lowerName.contains("lidl") || lowerName.contains("aldi")) {
            return GROCERIES;
        }
        if (lowerName.contains("lufthansa") || lowerName.contains("hotel") || 
            lowerName.contains("booking") || lowerName.contains("airbnb")) {
            return TRAVEL;
        }
        if (lowerName.contains("shell") || lowerName.contains("aral") || 
            lowerName.contains("db ") || lowerName.contains("mvv")) {
            return TRANSPORT;
        }
        if (lowerName.contains("amazon") || lowerName.contains("zalando") || 
            lowerName.contains("mediamarkt") || lowerName.contains("ikea")) {
            return SHOPPING;
        }
        if (lowerName.contains("netflix") || lowerName.contains("spotify") || 
            lowerName.contains("cinema") || lowerName.contains("ticketmaster")) {
            return ENTERTAINMENT;
        }
        if (lowerName.contains("vodafone") || lowerName.contains("telekom") || 
            lowerName.contains("e.on") || lowerName.contains("vattenfall")) {
            return UTILITIES;
        }
        if (lowerName.contains("apotheke") || lowerName.contains("pharmacy") || 
            lowerName.contains("fitness") || lowerName.contains("arzt")) {
            return HEALTH;
        }
        
        return OTHER;
    }
}
