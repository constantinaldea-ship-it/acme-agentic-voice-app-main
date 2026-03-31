package com.voicebanking.agent.nonbanking.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * Eligibility criteria for non-banking services.
 * 
 * Services may require:
 * - Minimum card tier
 * - Specific account type
 * - Minimum tenure (years as customer)
 * - Minimum balance
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class ServiceEligibility {
    
    private final CardTier minimumCardTier;
    private final String requiredAccountType;  // null = any account
    private final int minimumTenureYears;
    private final double minimumBalance;
    private final boolean allCustomers;  // true = no restrictions

    private ServiceEligibility(Builder builder) {
        this.minimumCardTier = builder.minimumCardTier;
        this.requiredAccountType = builder.requiredAccountType;
        this.minimumTenureYears = builder.minimumTenureYears;
        this.minimumBalance = builder.minimumBalance;
        this.allCustomers = builder.allCustomers;
    }

    public CardTier getMinimumCardTier() { return minimumCardTier; }
    public String getRequiredAccountType() { return requiredAccountType; }
    public int getMinimumTenureYears() { return minimumTenureYears; }
    public double getMinimumBalance() { return minimumBalance; }
    public boolean isAllCustomers() { return allCustomers; }

    /**
     * Check if a customer with given attributes is eligible.
     */
    public boolean isEligible(CardTier customerTier, String accountType, 
                              int tenureYears, double balance) {
        if (allCustomers) {
            return true;
        }
        
        if (minimumCardTier != null && customerTier != null) {
            if (!customerTier.meetsRequirement(minimumCardTier)) {
                return false;
            }
        }
        
        if (requiredAccountType != null && accountType != null) {
            if (!requiredAccountType.equalsIgnoreCase(accountType)) {
                return false;
            }
        }
        
        if (tenureYears < minimumTenureYears) {
            return false;
        }
        
        return !(balance < minimumBalance);
    }

    /**
     * Get the reason why customer is not eligible.
     */
    public String getIneligibilityReason(CardTier customerTier, String lang) {
        if (allCustomers) {
            return null;
        }
        
        if (minimumCardTier != null && customerTier != null 
                && !customerTier.meetsRequirement(minimumCardTier)) {
            boolean isGerman = "de".equalsIgnoreCase(lang);
            return isGerman 
                ? "Erfordert mindestens " + minimumCardTier.getNameDe() + " Karte"
                : "Requires at least " + minimumCardTier.getNameEn() + " card";
        }
        
        return null;
    }

    /**
     * Get upgrade suggestion for ineligible customer.
     */
    public String getUpgradeSuggestion(CardTier customerTier, String lang) {
        if (minimumCardTier == null || allCustomers) {
            return null;
        }
        
        boolean isGerman = "de".equalsIgnoreCase(lang);
        return isGerman 
            ? "Mit einem Upgrade auf die " + minimumCardTier.getNameDe() + 
              " Karte erhalten Sie Zugang zu diesem Vorteil."
            : "With an upgrade to the " + minimumCardTier.getNameEn() + 
              " card, you would have access to this benefit.";
    }

    /**
     * Format eligibility for display.
     */
    public String formatEligibility(String lang) {
        if (allCustomers) {
            return "de".equalsIgnoreCase(lang) ? "Alle Kunden" : "All customers";
        }
        
        StringBuilder sb = new StringBuilder();
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        if (minimumCardTier != null) {
            sb.append(minimumCardTier.getName(lang));
            if (minimumCardTier != CardTier.BLACK) {
                sb.append(isGerman ? " oder höher" : " or higher");
            }
        }
        
        return sb.toString();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("minimumCardTier", minimumCardTier != null ? minimumCardTier.name() : null);
        map.put("requiredAccountType", requiredAccountType);
        map.put("minimumTenureYears", minimumTenureYears);
        map.put("minimumBalance", minimumBalance);
        map.put("allCustomers", allCustomers);
        return map;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static ServiceEligibility forAll() {
        return new Builder().allCustomers(true).build();
    }

    public static ServiceEligibility forGoldAndAbove() {
        return new Builder().minimumCardTier(CardTier.GOLD).build();
    }

    public static ServiceEligibility forPlatinumAndAbove() {
        return new Builder().minimumCardTier(CardTier.PLATINUM).build();
    }

    public static class Builder {
        private CardTier minimumCardTier;
        private String requiredAccountType;
        private int minimumTenureYears = 0;
        private double minimumBalance = 0;
        private boolean allCustomers = false;

        public Builder minimumCardTier(CardTier tier) { 
            this.minimumCardTier = tier; 
            return this; 
        }
        
        public Builder requiredAccountType(String type) { 
            this.requiredAccountType = type; 
            return this; 
        }
        
        public Builder minimumTenureYears(int years) { 
            this.minimumTenureYears = years; 
            return this; 
        }
        
        public Builder minimumBalance(double balance) { 
            this.minimumBalance = balance; 
            return this; 
        }
        
        public Builder allCustomers(boolean all) { 
            this.allCustomers = all; 
            return this; 
        }

        public ServiceEligibility build() {
            return new ServiceEligibility(this);
        }
    }
}
