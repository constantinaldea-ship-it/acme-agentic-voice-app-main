package com.voicebanking.agent.product.domain;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Base product model for all Acme Bank products.
 * 
 * Extended by CreditCardProduct, GiroKontoProduct, and DebitCardProduct
 * for category-specific attributes.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class Product {
    
    protected String id;
    protected String nameEn;
    protected String nameDe;
    protected String descriptionEn;
    protected String descriptionDe;
    protected ProductCategory category;
    protected List<ProductFeature> features = new ArrayList<>();
    protected FeeSchedule feeSchedule;
    protected EligibilityCriteria eligibility;
    protected String termsAndConditionsUrl;
    protected String applicationUrl;
    protected boolean isActive = true;
    protected String comparisonGroup; // For grouping comparable products
    
    protected Product() {}
    
    public String getId() { return id; }
    public String getNameEn() { return nameEn; }
    public String getNameDe() { return nameDe; }
    public String getDescriptionEn() { return descriptionEn; }
    public String getDescriptionDe() { return descriptionDe; }
    public ProductCategory getCategory() { return category; }
    public List<ProductFeature> getFeatures() { return List.copyOf(features); }
    public FeeSchedule getFeeSchedule() { return feeSchedule; }
    public EligibilityCriteria getEligibility() { return eligibility; }
    public String getTermsAndConditionsUrl() { return termsAndConditionsUrl; }
    public String getApplicationUrl() { return applicationUrl; }
    public boolean isActive() { return isActive; }
    public String getComparisonGroup() { return comparisonGroup; }
    
    public String getName(String lang) {
        return "de".equalsIgnoreCase(lang) ? nameDe : nameEn;
    }
    
    public String getDescription(String lang) {
        return "de".equalsIgnoreCase(lang) ? descriptionDe : descriptionEn;
    }
    
    /**
     * Get highlighted features for quick summary.
     * @return list of highlight features
     */
    public List<ProductFeature> getHighlightFeatures() {
        return features.stream()
            .filter(ProductFeature::isHighlight)
            .collect(Collectors.toList());
    }
    
    /**
     * Format product summary for voice output.
     * @param lang language code
     * @return voice-friendly product summary
     */
    public String formatForVoice(String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append(getName(lang)).append(": ");
        sb.append(getDescription(lang)).append(" ");
        
        if (feeSchedule != null) {
            Fee primary = feeSchedule.getPrimaryFee();
            if (primary != null) {
                if (primary.getFrequency() == FeeFrequency.MONTHLY) {
                    sb.append("Monthly fee: €").append(primary.getAmount()).append(". ");
                } else {
                    sb.append("Annual fee: €").append(primary.getAmount()).append(". ");
                }
            }
        }
        
        List<ProductFeature> highlights = getHighlightFeatures();
        if (!highlights.isEmpty()) {
            sb.append("Key features: ");
            for (int i = 0; i < Math.min(3, highlights.size()); i++) {
                if (i > 0) sb.append(", ");
                sb.append(highlights.get(i).getName(lang));
            }
            sb.append(".");
        }
        
        return sb.toString();
    }
    
    /**
     * Format as brief listing entry.
     * @param lang language code
     * @return brief voice listing
     */
    public String formatAsBriefListing(String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append(getName(lang));
        
        if (feeSchedule != null) {
            Fee primary = feeSchedule.getPrimaryFee();
            if (primary != null) {
                if (primary.getFrequency() == FeeFrequency.MONTHLY) {
                    sb.append(" at €").append(primary.getAmount()).append(" per month");
                } else {
                    sb.append(" at €").append(primary.getAmount()).append(" per year");
                }
            }
        }
        
        return sb.toString();
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("nameEn", nameEn);
        map.put("nameDe", nameDe);
        map.put("descriptionEn", descriptionEn);
        map.put("descriptionDe", descriptionDe);
        map.put("category", category != null ? category.name() : null);
        map.put("features", features.stream().map(ProductFeature::toMap).collect(Collectors.toList()));
        map.put("feeSchedule", feeSchedule != null ? feeSchedule.toMap() : null);
        map.put("eligibility", eligibility != null ? eligibility.toMap() : null);
        map.put("termsAndConditionsUrl", termsAndConditionsUrl);
        map.put("applicationUrl", applicationUrl);
        map.put("isActive", isActive);
        map.put("comparisonGroup", comparisonGroup);
        return map;
    }
    
    public Map<String, Object> toBriefMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("nameEn", nameEn);
        map.put("nameDe", nameDe);
        map.put("category", category != null ? category.name() : null);
        if (feeSchedule != null) {
            Fee primary = feeSchedule.getPrimaryFee();
            if (primary != null) {
                map.put("primaryFeeAmount", primary.getAmount());
                map.put("primaryFeeFrequency", primary.getFrequency().name());
            }
        }
        return map;
    }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        protected final Product product;
        
        public Builder() {
            this.product = new Product();
        }
        
        protected Builder(Product product) {
            this.product = product;
        }
        
        public Builder id(String id) { product.id = id; return this; }
        public Builder nameEn(String n) { product.nameEn = n; return this; }
        public Builder nameDe(String n) { product.nameDe = n; return this; }
        public Builder descriptionEn(String d) { product.descriptionEn = d; return this; }
        public Builder descriptionDe(String d) { product.descriptionDe = d; return this; }
        public Builder category(ProductCategory c) { product.category = c; return this; }
        public Builder features(List<ProductFeature> f) { product.features = new ArrayList<>(f); return this; }
        public Builder addFeature(ProductFeature f) { product.features.add(f); return this; }
        public Builder feeSchedule(FeeSchedule f) { product.feeSchedule = f; return this; }
        public Builder eligibility(EligibilityCriteria e) { product.eligibility = e; return this; }
        public Builder termsAndConditionsUrl(String u) { product.termsAndConditionsUrl = u; return this; }
        public Builder applicationUrl(String u) { product.applicationUrl = u; return this; }
        public Builder isActive(boolean a) { product.isActive = a; return this; }
        public Builder comparisonGroup(String g) { product.comparisonGroup = g; return this; }
        
        public Product build() { return product; }
    }
}
