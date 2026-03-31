package com.voicebanking.agent.product.domain;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Complete fee schedule for a banking product.
 * 
 * Contains all fees associated with a product, organized by type.
 * Provides methods for calculating total annual costs and formatting for voice.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class FeeSchedule {
    
    private String productId;
    private String productName;
    private ProductCategory category;
    private List<Fee> fees = new ArrayList<>();
    private String effectiveDate;
    private String termsAndConditionsUrl;
    
    private FeeSchedule() {}
    
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public ProductCategory getCategory() { return category; }
    public List<Fee> getFees() { return List.copyOf(fees); }
    public String getEffectiveDate() { return effectiveDate; }
    public String getTermsAndConditionsUrl() { return termsAndConditionsUrl; }
    
    /**
     * Get fees filtered by type.
     * @param type the fee type to filter by
     * @return list of fees of the specified type
     */
    public List<Fee> getFeesByType(FeeType type) {
        return fees.stream()
            .filter(f -> f.getType() == type)
            .collect(Collectors.toList());
    }
    
    /**
     * Get the primary periodic fee (monthly or annual).
     * @return the main recurring fee, or null if none
     */
    public Fee getPrimaryFee() {
        // First try annual fee
        return fees.stream()
            .filter(f -> f.getType() == FeeType.PERIODIC)
            .filter(f -> f.getFrequency() == FeeFrequency.ANNUAL || f.getFrequency() == FeeFrequency.MONTHLY)
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Calculate estimated annual cost from periodic fees.
     * @return total annual cost from periodic fees
     */
    public BigDecimal calculateAnnualCost() {
        return fees.stream()
            .filter(f -> f.getType() == FeeType.PERIODIC)
            .map(Fee::calculateAnnualCost)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    /**
     * Format fee schedule for voice output.
     * @param lang language code
     * @return voice-friendly fee summary
     */
    public String formatForVoice(String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fees for ").append(productName).append(": ");
        
        Fee primary = getPrimaryFee();
        if (primary != null) {
            if (primary.getFrequency() == FeeFrequency.MONTHLY) {
                sb.append("The monthly fee is €").append(primary.getAmount());
            } else {
                sb.append("The annual fee is €").append(primary.getAmount());
            }
            
            if (primary.getWaivers(lang) != null && !primary.getWaivers(lang).isBlank()) {
                sb.append(", ").append(primary.getWaivers(lang));
            }
            sb.append(". ");
        }
        
        long transactionFeeCount = fees.stream()
            .filter(f -> f.getType() == FeeType.TRANSACTION)
            .count();
        
        if (transactionFeeCount > 0) {
            sb.append("There are ").append(transactionFeeCount).append(" transaction fees. ");
            sb.append("Would you like details on a specific fee?");
        }
        
        return sb.toString();
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("productId", productId);
        map.put("productName", productName);
        map.put("category", category != null ? category.name() : null);
        map.put("fees", fees.stream().map(Fee::toMap).collect(Collectors.toList()));
        map.put("effectiveDate", effectiveDate);
        map.put("termsAndConditionsUrl", termsAndConditionsUrl);
        map.put("annualCostEstimate", calculateAnnualCost());
        return map;
    }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private final FeeSchedule schedule = new FeeSchedule();
        
        public Builder productId(String id) { schedule.productId = id; return this; }
        public Builder productName(String name) { schedule.productName = name; return this; }
        public Builder category(ProductCategory cat) { schedule.category = cat; return this; }
        public Builder fees(List<Fee> fees) { schedule.fees = new ArrayList<>(fees); return this; }
        public Builder addFee(Fee fee) { schedule.fees.add(fee); return this; }
        public Builder effectiveDate(String date) { schedule.effectiveDate = date; return this; }
        public Builder termsAndConditionsUrl(String url) { schedule.termsAndConditionsUrl = url; return this; }
        
        public FeeSchedule build() { return schedule; }
    }
}
