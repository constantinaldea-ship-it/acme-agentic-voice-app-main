package com.voicebanking.agent.product.domain;

import java.util.*;

/**
 * Represents a feature or benefit of a banking product.
 * 
 * Features are presented as bullet points in comparisons and
 * as natural language in voice responses.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class ProductFeature {
    
    private String id;
    private String nameEn;
    private String nameDe;
    private String descriptionEn;
    private String descriptionDe;
    private String category; // e.g., "insurance", "rewards", "convenience"
    private boolean isHighlight; // Featured in quick summary
    private String valueEn; // e.g., "Included", "€50,000", "Unlimited"
    private String valueDe;
    
    private ProductFeature() {}
    
    public String getId() { return id; }
    public String getNameEn() { return nameEn; }
    public String getNameDe() { return nameDe; }
    public String getDescriptionEn() { return descriptionEn; }
    public String getDescriptionDe() { return descriptionDe; }
    public String getCategory() { return category; }
    public boolean isHighlight() { return isHighlight; }
    public String getValueEn() { return valueEn; }
    public String getValueDe() { return valueDe; }
    
    public String getName(String lang) {
        return "de".equalsIgnoreCase(lang) ? nameDe : nameEn;
    }
    
    public String getDescription(String lang) {
        return "de".equalsIgnoreCase(lang) ? descriptionDe : descriptionEn;
    }
    
    public String getValue(String lang) {
        return "de".equalsIgnoreCase(lang) ? valueDe : valueEn;
    }
    
    /**
     * Format feature for voice output.
     * @param lang language code
     * @return voice-friendly feature description
     */
    public String formatForVoice(String lang) {
        String value = getValue(lang);
        if (value != null && !value.isBlank()) {
            return getName(lang) + ": " + value;
        }
        return getName(lang);
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("nameEn", nameEn);
        map.put("nameDe", nameDe);
        map.put("descriptionEn", descriptionEn);
        map.put("descriptionDe", descriptionDe);
        map.put("category", category);
        map.put("isHighlight", isHighlight);
        map.put("valueEn", valueEn);
        map.put("valueDe", valueDe);
        return map;
    }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private final ProductFeature feature = new ProductFeature();
        
        public Builder id(String id) { feature.id = id; return this; }
        public Builder nameEn(String n) { feature.nameEn = n; return this; }
        public Builder nameDe(String n) { feature.nameDe = n; return this; }
        public Builder descriptionEn(String d) { feature.descriptionEn = d; return this; }
        public Builder descriptionDe(String d) { feature.descriptionDe = d; return this; }
        public Builder category(String c) { feature.category = c; return this; }
        public Builder isHighlight(boolean h) { feature.isHighlight = h; return this; }
        public Builder valueEn(String v) { feature.valueEn = v; return this; }
        public Builder valueDe(String v) { feature.valueDe = v; return this; }
        
        public ProductFeature build() { return feature; }
    }
}
