package com.voicebanking.agent.nonbanking.domain;

/**
 * Card tier levels that determine eligibility for non-banking services.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum CardTier {
    
    STANDARD(1, "Standard", "Standard"),
    GOLD(2, "Gold", "Gold"),
    PLATINUM(3, "Platinum", "Platin"),
    BLACK(4, "Black", "Schwarz");

    private final int level;
    private final String nameEn;
    private final String nameDe;

    CardTier(int level, String nameEn, String nameDe) {
        this.level = level;
        this.nameEn = nameEn;
        this.nameDe = nameDe;
    }

    public int getLevel() {
        return level;
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

    /**
     * Check if this tier meets or exceeds the required tier.
     * @param required the minimum required tier
     * @return true if eligible
     */
    public boolean meetsRequirement(CardTier required) {
        return this.level >= required.level;
    }

    /**
     * Get the next tier for upgrade messaging.
     * @return next tier or null if already at highest
     */
    public CardTier getNextTier() {
        return switch (this) {
            case STANDARD -> GOLD;
            case GOLD -> PLATINUM;
            case PLATINUM -> BLACK;
            case BLACK -> null;
        };
    }

    /**
     * Get tier from string, case-insensitive.
     */
    public static CardTier fromString(String tier) {
        if (tier == null) return STANDARD;
        return switch (tier.toUpperCase()) {
            case "GOLD" -> GOLD;
            case "PLATINUM", "PLATIN" -> PLATINUM;
            case "BLACK", "SCHWARZ" -> BLACK;
            default -> STANDARD;
        };
    }
}
