package com.voicebanking.agent.product.domain;

/**
 * Frequency at which fees are charged.
 * 
 * Used for clear voice presentation:
 * "The annual fee is €80" vs "Each ATM withdrawal costs €1.50"
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum FeeFrequency {
    
    /**
     * One-time fee (card replacement, account opening).
     */
    ONE_TIME("once", "einmalig"),
    
    /**
     * Per-use fee (per transaction, per withdrawal).
     */
    PER_USE("per use", "pro Nutzung"),
    
    /**
     * Monthly recurring fee.
     */
    MONTHLY("monthly", "monatlich"),
    
    /**
     * Quarterly recurring fee.
     */
    QUARTERLY("quarterly", "vierteljährlich"),
    
    /**
     * Annual recurring fee.
     */
    ANNUAL("annual", "jährlich");
    
    private final String displayNameEn;
    private final String displayNameDe;
    
    FeeFrequency(String displayNameEn, String displayNameDe) {
        this.displayNameEn = displayNameEn;
        this.displayNameDe = displayNameDe;
    }
    
    public String getDisplayNameEn() {
        return displayNameEn;
    }
    
    public String getDisplayNameDe() {
        return displayNameDe;
    }
    
    public String getDisplayName(String lang) {
        return "de".equalsIgnoreCase(lang) ? displayNameDe : displayNameEn;
    }
    
    /**
     * Get multiplier to calculate annual cost.
     * @return multiplier for annual cost calculation
     */
    public int getAnnualMultiplier() {
        return switch (this) {
            case ONE_TIME -> 1;
            case PER_USE -> 0; // Cannot calculate without usage data
            case MONTHLY -> 12;
            case QUARTERLY -> 4;
            case ANNUAL -> 1;
        };
    }
}
