package com.voicebanking.agent.personalfinance.domain;

/**
 * Personal finance spending categories.
 *
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum SpendingCategory {
    HOUSING("Housing", "Wohnen"),
    FOOD("Food", "Lebensmittel"),
    TRANSPORT("Transport", "Transport"),
    SHOPPING("Shopping", "Einkaufen"),
    ENTERTAINMENT("Entertainment", "Unterhaltung"),
    HEALTH("Health", "Gesundheit"),
    FINANCIAL("Financial", "Finanzen"),
    OTHER("Other", "Sonstiges");

    private final String displayNameEn;
    private final String displayNameDe;

    SpendingCategory(String displayNameEn, String displayNameDe) {
        this.displayNameEn = displayNameEn;
        this.displayNameDe = displayNameDe;
    }

    public String getDisplayName(String language) {
        if (language == null) {
            return displayNameEn;
        }
        return language.toLowerCase().startsWith("de") ? displayNameDe : displayNameEn;
    }
}
