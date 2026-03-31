package com.voicebanking.agent.product.domain;

import java.math.BigDecimal;
import java.util.*;

/**
 * Credit Card product with card-specific attributes.
 * 
 * Products: DB MasterCard Standard, Gold, Platinum, Business
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class CreditCardProduct extends Product {
    
    private BigDecimal creditLimitMin;
    private BigDecimal creditLimitMax;
    private BigDecimal interestRate; // APR as percentage
    private boolean contactlessEnabled = true;
    private boolean applePaySupported = true;
    private boolean googlePaySupported = true;
    private String cardNetwork; // MasterCard, Visa
    private String rewardsProgram;
    private String insurancePackage;
    
    private CreditCardProduct() {
        this.category = ProductCategory.CREDIT_CARD;
    }
    
    public BigDecimal getCreditLimitMin() { return creditLimitMin; }
    public BigDecimal getCreditLimitMax() { return creditLimitMax; }
    public BigDecimal getInterestRate() { return interestRate; }
    public boolean isContactlessEnabled() { return contactlessEnabled; }
    public boolean isApplePaySupported() { return applePaySupported; }
    public boolean isGooglePaySupported() { return googlePaySupported; }
    public String getCardNetwork() { return cardNetwork; }
    public String getRewardsProgram() { return rewardsProgram; }
    public String getInsurancePackage() { return insurancePackage; }
    
    @Override
    public String formatForVoice(String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append(getName(lang)).append(": ");
        sb.append(getDescription(lang)).append(" ");
        
        if (feeSchedule != null) {
            Fee primary = feeSchedule.getPrimaryFee();
            if (primary != null) {
                sb.append("The annual fee is €").append(primary.getAmount());
                if (primary.getWaivers(lang) != null) {
                    sb.append(", ").append(primary.getWaivers(lang));
                }
                sb.append(". ");
            }
        }
        
        if (creditLimitMax != null) {
            sb.append("Credit limit up to €").append(creditLimitMax).append(". ");
        }
        
        if (insurancePackage != null && !insurancePackage.isBlank()) {
            sb.append("Includes ").append(insurancePackage).append(". ");
        }
        
        if (contactlessEnabled) {
            sb.append("Contactless payments enabled. ");
        }
        
        return sb.toString();
    }
    
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = super.toMap();
        map.put("creditLimitMin", creditLimitMin);
        map.put("creditLimitMax", creditLimitMax);
        map.put("interestRate", interestRate);
        map.put("contactlessEnabled", contactlessEnabled);
        map.put("applePaySupported", applePaySupported);
        map.put("googlePaySupported", googlePaySupported);
        map.put("cardNetwork", cardNetwork);
        map.put("rewardsProgram", rewardsProgram);
        map.put("insurancePackage", insurancePackage);
        return map;
    }
    
    public static CreditCardBuilder creditCardBuilder() { return new CreditCardBuilder(); }
    
    public static class CreditCardBuilder extends Product.Builder {
        private final CreditCardProduct ccProduct;
        
        public CreditCardBuilder() {
            super(new CreditCardProduct());
            this.ccProduct = (CreditCardProduct) product;
        }
        
        public CreditCardBuilder creditLimitMin(BigDecimal limit) { ccProduct.creditLimitMin = limit; return this; }
        public CreditCardBuilder creditLimitMin(double limit) { ccProduct.creditLimitMin = BigDecimal.valueOf(limit); return this; }
        public CreditCardBuilder creditLimitMax(BigDecimal limit) { ccProduct.creditLimitMax = limit; return this; }
        public CreditCardBuilder creditLimitMax(double limit) { ccProduct.creditLimitMax = BigDecimal.valueOf(limit); return this; }
        public CreditCardBuilder interestRate(BigDecimal rate) { ccProduct.interestRate = rate; return this; }
        public CreditCardBuilder interestRate(double rate) { ccProduct.interestRate = BigDecimal.valueOf(rate); return this; }
        public CreditCardBuilder contactlessEnabled(boolean c) { ccProduct.contactlessEnabled = c; return this; }
        public CreditCardBuilder applePaySupported(boolean a) { ccProduct.applePaySupported = a; return this; }
        public CreditCardBuilder googlePaySupported(boolean g) { ccProduct.googlePaySupported = g; return this; }
        public CreditCardBuilder cardNetwork(String n) { ccProduct.cardNetwork = n; return this; }
        public CreditCardBuilder rewardsProgram(String r) { ccProduct.rewardsProgram = r; return this; }
        public CreditCardBuilder insurancePackage(String i) { ccProduct.insurancePackage = i; return this; }
        
        // Re-expose parent builder methods with correct return type
        @Override public CreditCardBuilder id(String id) { super.id(id); return this; }
        @Override public CreditCardBuilder nameEn(String n) { super.nameEn(n); return this; }
        @Override public CreditCardBuilder nameDe(String n) { super.nameDe(n); return this; }
        @Override public CreditCardBuilder descriptionEn(String d) { super.descriptionEn(d); return this; }
        @Override public CreditCardBuilder descriptionDe(String d) { super.descriptionDe(d); return this; }
        @Override public CreditCardBuilder features(List<ProductFeature> f) { super.features(f); return this; }
        @Override public CreditCardBuilder addFeature(ProductFeature f) { super.addFeature(f); return this; }
        @Override public CreditCardBuilder feeSchedule(FeeSchedule f) { super.feeSchedule(f); return this; }
        @Override public CreditCardBuilder eligibility(EligibilityCriteria e) { super.eligibility(e); return this; }
        @Override public CreditCardBuilder termsAndConditionsUrl(String u) { super.termsAndConditionsUrl(u); return this; }
        @Override public CreditCardBuilder applicationUrl(String u) { super.applicationUrl(u); return this; }
        @Override public CreditCardBuilder isActive(boolean a) { super.isActive(a); return this; }
        @Override public CreditCardBuilder comparisonGroup(String g) { super.comparisonGroup(g); return this; }
        
        @Override
        public CreditCardProduct build() { return ccProduct; }
    }
}
