package com.voicebanking.agent.nonbanking.domain;

/**
 * Categories of non-banking services offered by Acme Bank.
 * 
 * <ul>
 *   <li>INSURANCE - Travel, liability, purchase protection</li>
 *   <li>TRAVEL - Lounges, Miles & More, concierge</li>
 *   <li>LIFESTYLE - Events, concierge, exclusive offers</li>
 *   <li>PARTNERS - Retail discounts, cashback</li>
 *   <li>DIGITAL - Identity protection, security services</li>
 * </ul>
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum ServiceCategory {
    
    INSURANCE("Insurance Services", "Versicherungsleistungen", 
              "Travel, liability, and purchase protection insurance"),
    
    TRAVEL("Travel Services", "Reiseleistungen",
           "Airport lounges, Miles & More, travel concierge"),
    
    LIFESTYLE("Lifestyle Benefits", "Lifestyle-Vorteile",
              "Events, concierge services, exclusive experiences"),
    
    PARTNERS("Partner Offers", "Partnerangebote",
             "Retail discounts, cashback programs, promotions"),
    
    DIGITAL("Digital Services", "Digitale Dienste",
            "Identity protection, security monitoring, digital tools");

    private final String nameEn;
    private final String nameDe;
    private final String description;

    ServiceCategory(String nameEn, String nameDe, String description) {
        this.nameEn = nameEn;
        this.nameDe = nameDe;
        this.description = description;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getNameDe() {
        return nameDe;
    }

    public String getName(String lang) {
        return "de".equalsIgnoreCase(lang) ? nameDe : nameEn;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this category requires premium products.
     */
    public boolean requiresPremium() {
        return this == LIFESTYLE || this == DIGITAL;
    }

    /**
     * Check if this category is card-based eligibility.
     */
    public boolean isCardBased() {
        return this == INSURANCE || this == TRAVEL;
    }
}
