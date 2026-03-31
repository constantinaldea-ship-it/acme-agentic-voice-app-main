package com.voicebanking.agent.product.domain;

/**
 * Enumeration of product categories offered by Acme Bank.
 * 
 * Product categories align with the Scope Document Slide 10 MVP scope:
 * - Credit Cards (general product information)
 * - Giro Konto (checking accounts)
 * - Debit Cards (bundled with Giro Konto)
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum ProductCategory {
    
    /**
     * Credit card products (MasterCard Standard, Gold, Platinum, Business).
     */
    CREDIT_CARD("Credit Card", "Kreditkarte"),
    
    /**
     * Giro Konto (checking account) products (AktivKonto, BestKonto, Junges Konto, Basiskonto).
     */
    GIRO_KONTO("Giro Konto", "Girokonto"),
    
    /**
     * Debit card products (Debit Mastercard, girocard).
     */
    DEBIT_CARD("Debit Card", "Debitkarte");
    
    private final String displayNameEn;
    private final String displayNameDe;
    
    ProductCategory(String displayNameEn, String displayNameDe) {
        this.displayNameEn = displayNameEn;
        this.displayNameDe = displayNameDe;
    }
    
    public String getDisplayNameEn() {
        return displayNameEn;
    }
    
    public String getDisplayNameDe() {
        return displayNameDe;
    }
    
    /**
     * Get display name in specified language.
     * @param lang ISO language code ("de" or "en")
     * @return localized display name
     */
    public String getDisplayName(String lang) {
        return "de".equalsIgnoreCase(lang) ? displayNameDe : displayNameEn;
    }
}
