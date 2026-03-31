package com.voicebanking.agent.product.domain;

/**
 * Types of fees that can be charged for banking products.
 * 
 * Fee types are structured to enable clear voice presentation:
 * "The monthly fee is €12.90" or "ATM withdrawals cost €1.50 each"
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum FeeType {
    
    /**
     * Recurring periodic fees (monthly, annual).
     */
    PERIODIC("Periodic Fee", "Periodische Gebühr"),
    
    /**
     * Per-transaction fees (ATM withdrawal, transfer).
     */
    TRANSACTION("Transaction Fee", "Transaktionsgebühr"),
    
    /**
     * Service fees (replacement card, paper statement).
     */
    SERVICE("Service Fee", "Servicegebühr"),
    
    /**
     * Penalty fees (overdraft, returned payment).
     */
    PENALTY("Penalty Fee", "Strafgebühr"),
    
    /**
     * Interest charges (credit card, overdraft).
     */
    INTEREST("Interest", "Zinsen");
    
    private final String displayNameEn;
    private final String displayNameDe;
    
    FeeType(String displayNameEn, String displayNameDe) {
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
}
