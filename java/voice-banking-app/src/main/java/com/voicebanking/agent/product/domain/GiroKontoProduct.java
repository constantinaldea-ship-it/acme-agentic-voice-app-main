package com.voicebanking.agent.product.domain;

import java.math.BigDecimal;
import java.util.*;

/**
 * Giro Konto (checking account) product with account-specific attributes.
 * 
 * Products: AktivKonto, BestKonto, Junges Konto, Basiskonto
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class GiroKontoProduct extends Product {
    
    private Integer includedTransactionsPerMonth; // null = unlimited
    private BigDecimal overdraftInterestRate;
    private boolean debitCardIncluded = true;
    private String debitCardType; // "Debit Mastercard", "girocard"
    private Integer freeAtmWithdrawals; // null = unlimited
    private boolean onlineBankingIncluded = true;
    private boolean mobileBankingIncluded = true;
    private boolean instantTransfersEnabled = true;
    private String targetAudienceEn;
    private String targetAudienceDe;
    
    private GiroKontoProduct() {
        this.category = ProductCategory.GIRO_KONTO;
    }
    
    public Integer getIncludedTransactionsPerMonth() { return includedTransactionsPerMonth; }
    public BigDecimal getOverdraftInterestRate() { return overdraftInterestRate; }
    public boolean isDebitCardIncluded() { return debitCardIncluded; }
    public String getDebitCardType() { return debitCardType; }
    public Integer getFreeAtmWithdrawals() { return freeAtmWithdrawals; }
    public boolean isOnlineBankingIncluded() { return onlineBankingIncluded; }
    public boolean isMobileBankingIncluded() { return mobileBankingIncluded; }
    public boolean isInstantTransfersEnabled() { return instantTransfersEnabled; }
    public String getTargetAudienceEn() { return targetAudienceEn; }
    public String getTargetAudienceDe() { return targetAudienceDe; }
    
    public String getTargetAudience(String lang) {
        return "de".equalsIgnoreCase(lang) ? targetAudienceDe : targetAudienceEn;
    }
    
    @Override
    public String formatForVoice(String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append(getName(lang)).append(": ");
        sb.append(getDescription(lang)).append(" ");
        
        if (feeSchedule != null) {
            Fee primary = feeSchedule.getPrimaryFee();
            if (primary != null) {
                if (primary.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                    sb.append("No monthly fee. ");
                } else {
                    sb.append("The monthly fee is €").append(primary.getAmount());
                    if (primary.getWaivers(lang) != null) {
                        sb.append(", ").append(primary.getWaivers(lang));
                    }
                    sb.append(". ");
                }
            }
        }
        
        if (includedTransactionsPerMonth == null) {
            sb.append("Unlimited transactions included. ");
        } else {
            sb.append(includedTransactionsPerMonth).append(" transactions per month included. ");
        }
        
        if (debitCardIncluded && debitCardType != null) {
            sb.append("Includes a ").append(debitCardType).append(". ");
        }
        
        return sb.toString();
    }
    
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = super.toMap();
        map.put("includedTransactionsPerMonth", includedTransactionsPerMonth);
        map.put("overdraftInterestRate", overdraftInterestRate);
        map.put("debitCardIncluded", debitCardIncluded);
        map.put("debitCardType", debitCardType);
        map.put("freeAtmWithdrawals", freeAtmWithdrawals);
        map.put("onlineBankingIncluded", onlineBankingIncluded);
        map.put("mobileBankingIncluded", mobileBankingIncluded);
        map.put("instantTransfersEnabled", instantTransfersEnabled);
        map.put("targetAudienceEn", targetAudienceEn);
        map.put("targetAudienceDe", targetAudienceDe);
        return map;
    }
    
    public static GiroKontoBuilder giroKontoBuilder() { return new GiroKontoBuilder(); }
    
    public static class GiroKontoBuilder extends Product.Builder {
        private final GiroKontoProduct gkProduct;
        
        public GiroKontoBuilder() {
            super(new GiroKontoProduct());
            this.gkProduct = (GiroKontoProduct) product;
        }
        
        public GiroKontoBuilder includedTransactionsPerMonth(Integer t) { gkProduct.includedTransactionsPerMonth = t; return this; }
        public GiroKontoBuilder overdraftInterestRate(BigDecimal r) { gkProduct.overdraftInterestRate = r; return this; }
        public GiroKontoBuilder overdraftInterestRate(double r) { gkProduct.overdraftInterestRate = BigDecimal.valueOf(r); return this; }
        public GiroKontoBuilder debitCardIncluded(boolean d) { gkProduct.debitCardIncluded = d; return this; }
        public GiroKontoBuilder debitCardType(String t) { gkProduct.debitCardType = t; return this; }
        public GiroKontoBuilder freeAtmWithdrawals(Integer f) { gkProduct.freeAtmWithdrawals = f; return this; }
        public GiroKontoBuilder onlineBankingIncluded(boolean o) { gkProduct.onlineBankingIncluded = o; return this; }
        public GiroKontoBuilder mobileBankingIncluded(boolean m) { gkProduct.mobileBankingIncluded = m; return this; }
        public GiroKontoBuilder instantTransfersEnabled(boolean i) { gkProduct.instantTransfersEnabled = i; return this; }
        public GiroKontoBuilder targetAudienceEn(String t) { gkProduct.targetAudienceEn = t; return this; }
        public GiroKontoBuilder targetAudienceDe(String t) { gkProduct.targetAudienceDe = t; return this; }
        
        // Re-expose parent builder methods with correct return type
        @Override public GiroKontoBuilder id(String id) { super.id(id); return this; }
        @Override public GiroKontoBuilder nameEn(String n) { super.nameEn(n); return this; }
        @Override public GiroKontoBuilder nameDe(String n) { super.nameDe(n); return this; }
        @Override public GiroKontoBuilder descriptionEn(String d) { super.descriptionEn(d); return this; }
        @Override public GiroKontoBuilder descriptionDe(String d) { super.descriptionDe(d); return this; }
        @Override public GiroKontoBuilder features(List<ProductFeature> f) { super.features(f); return this; }
        @Override public GiroKontoBuilder addFeature(ProductFeature f) { super.addFeature(f); return this; }
        @Override public GiroKontoBuilder feeSchedule(FeeSchedule f) { super.feeSchedule(f); return this; }
        @Override public GiroKontoBuilder eligibility(EligibilityCriteria e) { super.eligibility(e); return this; }
        @Override public GiroKontoBuilder termsAndConditionsUrl(String u) { super.termsAndConditionsUrl(u); return this; }
        @Override public GiroKontoBuilder applicationUrl(String u) { super.applicationUrl(u); return this; }
        @Override public GiroKontoBuilder isActive(boolean a) { super.isActive(a); return this; }
        @Override public GiroKontoBuilder comparisonGroup(String g) { super.comparisonGroup(g); return this; }
        
        @Override
        public GiroKontoProduct build() { return gkProduct; }
    }
}
