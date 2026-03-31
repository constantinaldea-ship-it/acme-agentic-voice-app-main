package com.voicebanking.agent.product.domain;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a fee charged for a banking product.
 * 
 * Supports both simple fees (fixed amount) and conditional fees
 * (waived if balance > X, free for first Y uses, etc.)
 * 
 * Example voice output: "The annual fee is €80, which is waived if you 
 * have a monthly income deposit of at least €1,200."
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class Fee {
    
    private String id;
    private String nameEn;
    private String nameDe;
    private BigDecimal amount;
    private String currency;
    private FeeType type;
    private FeeFrequency frequency;
    private String conditionEn;
    private String conditionDe;
    private String waiversEn;
    private String waiversDe;
    private boolean isPercentage;
    private BigDecimal percentageRate;
    
    private Fee() {}
    
    public String getId() { return id; }
    public String getNameEn() { return nameEn; }
    public String getNameDe() { return nameDe; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public FeeType getType() { return type; }
    public FeeFrequency getFrequency() { return frequency; }
    public String getConditionEn() { return conditionEn; }
    public String getConditionDe() { return conditionDe; }
    public String getWaiversEn() { return waiversEn; }
    public String getWaiversDe() { return waiversDe; }
    public boolean isPercentage() { return isPercentage; }
    public BigDecimal getPercentageRate() { return percentageRate; }
    
    public String getName(String lang) {
        return "de".equalsIgnoreCase(lang) ? nameDe : nameEn;
    }
    
    public String getCondition(String lang) {
        return "de".equalsIgnoreCase(lang) ? conditionDe : conditionEn;
    }
    
    public String getWaivers(String lang) {
        return "de".equalsIgnoreCase(lang) ? waiversDe : waiversEn;
    }
    
    /**
     * Format fee for voice output.
     * @param lang language code
     * @return voice-friendly fee description
     */
    public String formatForVoice(String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append(getName(lang)).append(": ");
        
        if (isPercentage) {
            sb.append(percentageRate).append("%");
        } else {
            sb.append("€").append(amount);
        }
        
        if (frequency != null && frequency != FeeFrequency.ONE_TIME) {
            sb.append(" ").append(frequency.getDisplayName(lang));
        }
        
        String waivers = getWaivers(lang);
        if (waivers != null && !waivers.isBlank()) {
            sb.append(" (").append(waivers).append(")");
        }
        
        return sb.toString();
    }
    
    /**
     * Calculate annual cost estimate for this fee.
     * Does not include per-use fees.
     * @return annual cost or null if cannot be calculated
     */
    public BigDecimal calculateAnnualCost() {
        if (frequency == null || frequency == FeeFrequency.PER_USE || isPercentage) {
            return null;
        }
        return amount.multiply(BigDecimal.valueOf(frequency.getAnnualMultiplier()));
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("nameEn", nameEn);
        map.put("nameDe", nameDe);
        map.put("amount", amount);
        map.put("currency", currency);
        map.put("type", type != null ? type.name() : null);
        map.put("frequency", frequency != null ? frequency.name() : null);
        map.put("conditionEn", conditionEn);
        map.put("conditionDe", conditionDe);
        map.put("waiversEn", waiversEn);
        map.put("waiversDe", waiversDe);
        map.put("isPercentage", isPercentage);
        map.put("percentageRate", percentageRate);
        return map;
    }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private final Fee fee = new Fee();
        
        public Builder id(String id) { fee.id = id; return this; }
        public Builder nameEn(String n) { fee.nameEn = n; return this; }
        public Builder nameDe(String n) { fee.nameDe = n; return this; }
        public Builder amount(BigDecimal a) { fee.amount = a; return this; }
        public Builder amount(double a) { fee.amount = BigDecimal.valueOf(a); return this; }
        public Builder currency(String c) { fee.currency = c; return this; }
        public Builder type(FeeType t) { fee.type = t; return this; }
        public Builder frequency(FeeFrequency f) { fee.frequency = f; return this; }
        public Builder conditionEn(String c) { fee.conditionEn = c; return this; }
        public Builder conditionDe(String c) { fee.conditionDe = c; return this; }
        public Builder waiversEn(String w) { fee.waiversEn = w; return this; }
        public Builder waiversDe(String w) { fee.waiversDe = w; return this; }
        public Builder isPercentage(boolean p) { fee.isPercentage = p; return this; }
        public Builder percentageRate(BigDecimal r) { fee.percentageRate = r; return this; }
        public Builder percentageRate(double r) { fee.percentageRate = BigDecimal.valueOf(r); return this; }
        
        public Fee build() { return fee; }
    }
}
