package com.voicebanking.agent.product.domain;

import java.util.*;

/**
 * Eligibility criteria for applying for a banking product.
 * 
 * Voice output example: "To apply for the Gold card, you need to be at least 
 * 18 years old and have a monthly income of at least €2,000."
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class EligibilityCriteria {
    
    private Integer minimumAge;
    private Integer maximumAge;
    private Double minimumIncome;
    private boolean requiresAccountWithBank;
    private String residencyRequirementEn;
    private String residencyRequirementDe;
    private List<String> additionalRequirementsEn = new ArrayList<>();
    private List<String> additionalRequirementsDe = new ArrayList<>();
    
    private EligibilityCriteria() {}
    
    public Integer getMinimumAge() { return minimumAge; }
    public Integer getMaximumAge() { return maximumAge; }
    public Double getMinimumIncome() { return minimumIncome; }
    public boolean isRequiresAccountWithBank() { return requiresAccountWithBank; }
    public String getResidencyRequirementEn() { return residencyRequirementEn; }
    public String getResidencyRequirementDe() { return residencyRequirementDe; }
    public List<String> getAdditionalRequirementsEn() { return List.copyOf(additionalRequirementsEn); }
    public List<String> getAdditionalRequirementsDe() { return List.copyOf(additionalRequirementsDe); }
    
    public String getResidencyRequirement(String lang) {
        return "de".equalsIgnoreCase(lang) ? residencyRequirementDe : residencyRequirementEn;
    }
    
    public List<String> getAdditionalRequirements(String lang) {
        return "de".equalsIgnoreCase(lang) ? additionalRequirementsDe : additionalRequirementsEn;
    }
    
    /**
     * Format eligibility for voice output.
     * @param productName name of the product
     * @param lang language code
     * @return voice-friendly eligibility description
     */
    public String formatForVoice(String productName, String lang) {
        List<String> requirements = new ArrayList<>();
        
        if (minimumAge != null) {
            if (maximumAge != null) {
                requirements.add("be between " + minimumAge + " and " + maximumAge + " years old");
            } else {
                requirements.add("be at least " + minimumAge + " years old");
            }
        }
        
        if (minimumIncome != null) {
            requirements.add("have a monthly income of at least €" + String.format("%.0f", minimumIncome));
        }
        
        if (requiresAccountWithBank) {
            requirements.add("have an account with Acme Bank");
        }
        
        String residency = getResidencyRequirement(lang);
        if (residency != null && !residency.isBlank()) {
            requirements.add(residency);
        }
        
        requirements.addAll(getAdditionalRequirements(lang));
        
        if (requirements.isEmpty()) {
            return "The " + productName + " is available to all customers.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("To apply for the ").append(productName).append(", you need to ");
        
        for (int i = 0; i < requirements.size(); i++) {
            if (i > 0 && i == requirements.size() - 1) {
                sb.append(", and ");
            } else if (i > 0) {
                sb.append(", ");
            }
            sb.append(requirements.get(i));
        }
        sb.append(".");
        
        return sb.toString();
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("minimumAge", minimumAge);
        map.put("maximumAge", maximumAge);
        map.put("minimumIncome", minimumIncome);
        map.put("requiresAccountWithBank", requiresAccountWithBank);
        map.put("residencyRequirementEn", residencyRequirementEn);
        map.put("residencyRequirementDe", residencyRequirementDe);
        map.put("additionalRequirementsEn", additionalRequirementsEn);
        map.put("additionalRequirementsDe", additionalRequirementsDe);
        return map;
    }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private final EligibilityCriteria criteria = new EligibilityCriteria();
        
        public Builder minimumAge(Integer age) { criteria.minimumAge = age; return this; }
        public Builder maximumAge(Integer age) { criteria.maximumAge = age; return this; }
        public Builder minimumIncome(Double income) { criteria.minimumIncome = income; return this; }
        public Builder requiresAccountWithBank(boolean req) { criteria.requiresAccountWithBank = req; return this; }
        public Builder residencyRequirementEn(String r) { criteria.residencyRequirementEn = r; return this; }
        public Builder residencyRequirementDe(String r) { criteria.residencyRequirementDe = r; return this; }
        public Builder additionalRequirementsEn(List<String> reqs) { 
            criteria.additionalRequirementsEn = new ArrayList<>(reqs); return this; 
        }
        public Builder additionalRequirementsDe(List<String> reqs) { 
            criteria.additionalRequirementsDe = new ArrayList<>(reqs); return this; 
        }
        public Builder addRequirementEn(String req) { 
            criteria.additionalRequirementsEn.add(req); return this; 
        }
        public Builder addRequirementDe(String req) { 
            criteria.additionalRequirementsDe.add(req); return this; 
        }
        
        public EligibilityCriteria build() { return criteria; }
    }
}
