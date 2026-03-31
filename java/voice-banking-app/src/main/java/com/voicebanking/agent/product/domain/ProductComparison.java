package com.voicebanking.agent.product.domain;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Result of comparing two products.
 * 
 * Provides factual comparison without recommendations.
 * Always includes a disclaimer about consulting an advisor.
 * 
 * Voice output example: "The Gold card has an annual fee of €80 with travel 
 * insurance included, while the Standard card is €35 without insurance. 
 * Would you like more details on either card?"
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class ProductComparison {
    
    private static final String DISCLAIMER_EN = "This comparison is for information only. " +
            "Please consult an advisor for personalized recommendations.";
    private static final String DISCLAIMER_DE = "Dieser Vergleich dient nur zur Information. " +
            "Bitte wenden Sie sich für eine persönliche Beratung an einen Berater.";
    
    private Product product1;
    private Product product2;
    private List<ComparisonPoint> comparisonPoints = new ArrayList<>();
    private List<String> product1AdvantagesEn = new ArrayList<>();
    private List<String> product1AdvantagesDe = new ArrayList<>();
    private List<String> product2AdvantagesEn = new ArrayList<>();
    private List<String> product2AdvantagesDe = new ArrayList<>();
    private BigDecimal annualCostDifference;
    
    private ProductComparison() {}
    
    public Product getProduct1() { return product1; }
    public Product getProduct2() { return product2; }
    public List<ComparisonPoint> getComparisonPoints() { return List.copyOf(comparisonPoints); }
    public List<String> getProduct1AdvantagesEn() { return List.copyOf(product1AdvantagesEn); }
    public List<String> getProduct1AdvantagesDe() { return List.copyOf(product1AdvantagesDe); }
    public List<String> getProduct2AdvantagesEn() { return List.copyOf(product2AdvantagesEn); }
    public List<String> getProduct2AdvantagesDe() { return List.copyOf(product2AdvantagesDe); }
    public BigDecimal getAnnualCostDifference() { return annualCostDifference; }
    
    public List<String> getProduct1Advantages(String lang) {
        return "de".equalsIgnoreCase(lang) ? product1AdvantagesDe : product1AdvantagesEn;
    }
    
    public List<String> getProduct2Advantages(String lang) {
        return "de".equalsIgnoreCase(lang) ? product2AdvantagesDe : product2AdvantagesEn;
    }
    
    public static String getDisclaimer(String lang) {
        return "de".equalsIgnoreCase(lang) ? DISCLAIMER_DE : DISCLAIMER_EN;
    }
    
    /**
     * Format comparison for voice output.
     * @param lang language code
     * @return voice-friendly comparison
     */
    public String formatForVoice(String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append("Comparing ").append(product1.getName(lang));
        sb.append(" and ").append(product2.getName(lang)).append(". ");
        
        // Focus on the main fee difference
        if (product1.getFeeSchedule() != null && product2.getFeeSchedule() != null) {
            Fee fee1 = product1.getFeeSchedule().getPrimaryFee();
            Fee fee2 = product2.getFeeSchedule().getPrimaryFee();
            
            if (fee1 != null && fee2 != null) {
                String period = fee1.getFrequency() == FeeFrequency.MONTHLY ? "monthly" : "annual";
                sb.append("The ").append(period).append(" fee for ");
                sb.append(product1.getName(lang)).append(" is €").append(fee1.getAmount());
                sb.append(", while ").append(product2.getName(lang));
                sb.append(" is €").append(fee2.getAmount()).append(". ");
            }
        }
        
        // Highlight key differences (up to 2)
        List<ComparisonPoint> keyDifferences = comparisonPoints.stream()
            .filter(ComparisonPoint::isSignificantDifference)
            .limit(2)
            .collect(Collectors.toList());
        
        for (ComparisonPoint point : keyDifferences) {
            sb.append(point.formatForVoice(lang)).append(" ");
        }
        
        sb.append("Would you like more details on either product? ");
        sb.append(getDisclaimer(lang));
        
        return sb.toString();
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("product1", product1.toBriefMap());
        map.put("product2", product2.toBriefMap());
        map.put("comparisonPoints", comparisonPoints.stream()
            .map(ComparisonPoint::toMap)
            .collect(Collectors.toList()));
        map.put("product1AdvantagesEn", product1AdvantagesEn);
        map.put("product1AdvantagesDe", product1AdvantagesDe);
        map.put("product2AdvantagesEn", product2AdvantagesEn);
        map.put("product2AdvantagesDe", product2AdvantagesDe);
        map.put("annualCostDifference", annualCostDifference);
        map.put("disclaimerEn", DISCLAIMER_EN);
        map.put("disclaimerDe", DISCLAIMER_DE);
        return map;
    }
    
    public static Builder builder() { return new Builder(); }
    
    public static class Builder {
        private final ProductComparison comparison = new ProductComparison();
        
        public Builder product1(Product p) { comparison.product1 = p; return this; }
        public Builder product2(Product p) { comparison.product2 = p; return this; }
        public Builder comparisonPoints(List<ComparisonPoint> points) { 
            comparison.comparisonPoints = new ArrayList<>(points); return this; 
        }
        public Builder addComparisonPoint(ComparisonPoint point) { 
            comparison.comparisonPoints.add(point); return this; 
        }
        public Builder product1AdvantagesEn(List<String> adv) { 
            comparison.product1AdvantagesEn = new ArrayList<>(adv); return this; 
        }
        public Builder product1AdvantagesDe(List<String> adv) { 
            comparison.product1AdvantagesDe = new ArrayList<>(adv); return this; 
        }
        public Builder product2AdvantagesEn(List<String> adv) { 
            comparison.product2AdvantagesEn = new ArrayList<>(adv); return this; 
        }
        public Builder product2AdvantagesDe(List<String> adv) { 
            comparison.product2AdvantagesDe = new ArrayList<>(adv); return this; 
        }
        public Builder annualCostDifference(BigDecimal diff) { 
            comparison.annualCostDifference = diff; return this; 
        }
        
        public ProductComparison build() { return comparison; }
    }
    
    /**
     * Single comparison point between two products.
     */
    public static class ComparisonPoint {
        private String attributeEn;
        private String attributeDe;
        private String product1ValueEn;
        private String product1ValueDe;
        private String product2ValueEn;
        private String product2ValueDe;
        private boolean significantDifference;
        
        private ComparisonPoint() {}
        
        public String getAttributeEn() { return attributeEn; }
        public String getAttributeDe() { return attributeDe; }
        public String getProduct1ValueEn() { return product1ValueEn; }
        public String getProduct1ValueDe() { return product1ValueDe; }
        public String getProduct2ValueEn() { return product2ValueEn; }
        public String getProduct2ValueDe() { return product2ValueDe; }
        public boolean isSignificantDifference() { return significantDifference; }
        
        public String getAttribute(String lang) {
            return "de".equalsIgnoreCase(lang) ? attributeDe : attributeEn;
        }
        
        public String getProduct1Value(String lang) {
            return "de".equalsIgnoreCase(lang) ? product1ValueDe : product1ValueEn;
        }
        
        public String getProduct2Value(String lang) {
            return "de".equalsIgnoreCase(lang) ? product2ValueDe : product2ValueEn;
        }
        
        public String formatForVoice(String lang) {
            return getAttribute(lang) + ": " + 
                   getProduct1Value(lang) + " vs " + getProduct2Value(lang);
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("attributeEn", attributeEn);
            map.put("attributeDe", attributeDe);
            map.put("product1ValueEn", product1ValueEn);
            map.put("product1ValueDe", product1ValueDe);
            map.put("product2ValueEn", product2ValueEn);
            map.put("product2ValueDe", product2ValueDe);
            map.put("significantDifference", significantDifference);
            return map;
        }
        
        public static PointBuilder pointBuilder() { return new PointBuilder(); }
        
        public static class PointBuilder {
            private final ComparisonPoint point = new ComparisonPoint();
            
            public PointBuilder attributeEn(String a) { point.attributeEn = a; return this; }
            public PointBuilder attributeDe(String a) { point.attributeDe = a; return this; }
            public PointBuilder product1ValueEn(String v) { point.product1ValueEn = v; return this; }
            public PointBuilder product1ValueDe(String v) { point.product1ValueDe = v; return this; }
            public PointBuilder product2ValueEn(String v) { point.product2ValueEn = v; return this; }
            public PointBuilder product2ValueDe(String v) { point.product2ValueDe = v; return this; }
            public PointBuilder significantDifference(boolean s) { point.significantDifference = s; return this; }
            
            public ComparisonPoint build() { return point; }
        }
    }
}
