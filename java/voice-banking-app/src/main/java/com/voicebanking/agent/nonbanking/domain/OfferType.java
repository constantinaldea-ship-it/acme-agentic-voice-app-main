package com.voicebanking.agent.nonbanking.domain;

/**
 * Types of partner offers available to Acme Bank customers.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum OfferType {
    
    DISCOUNT("Discount", "Rabatt", "Percentage or fixed amount discount"),
    CASHBACK("Cashback", "Cashback", "Money back on purchases"),
    EXCLUSIVE_ACCESS("Exclusive Access", "Exklusiver Zugang", "Access to exclusive events or sales"),
    FREE_SHIPPING("Free Shipping", "Kostenloser Versand", "Free shipping on orders"),
    FREE_GIFT("Free Gift", "Gratis-Geschenk", "Free item with purchase"),
    BONUS_POINTS("Bonus Points", "Bonuspunkte", "Extra reward points"),
    UPGRADE("Upgrade", "Upgrade", "Free service upgrade");

    private final String nameEn;
    private final String nameDe;
    private final String description;

    OfferType(String nameEn, String nameDe, String description) {
        this.nameEn = nameEn;
        this.nameDe = nameDe;
        this.description = description;
    }

    public String getNameEn() { return nameEn; }
    public String getNameDe() { return nameDe; }
    public String getDescription() { return description; }

    public String getName(String lang) {
        return "de".equalsIgnoreCase(lang) ? nameDe : nameEn;
    }

    /**
     * Parse offer type from string, case-insensitive.
     */
    public static OfferType fromString(String value) {
        if (value == null) return null;
        
        String upper = value.toUpperCase().replace(" ", "_").replace("-", "_");
        for (OfferType type : values()) {
            if (type.name().equals(upper)) {
                return type;
            }
        }
        
        // Try partial matching
        String lower = value.toLowerCase();
        if (lower.contains("discount") || lower.contains("rabatt")) return DISCOUNT;
        if (lower.contains("cashback")) return CASHBACK;
        if (lower.contains("exclusive") || lower.contains("access")) return EXCLUSIVE_ACCESS;
        if (lower.contains("shipping") || lower.contains("versand")) return FREE_SHIPPING;
        if (lower.contains("gift") || lower.contains("geschenk")) return FREE_GIFT;
        if (lower.contains("point") || lower.contains("punkt")) return BONUS_POINTS;
        if (lower.contains("upgrade")) return UPGRADE;
        
        return DISCOUNT; // Default
    }
}
