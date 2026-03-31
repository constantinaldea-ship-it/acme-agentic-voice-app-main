package com.voicebanking.agent.product.domain;

import java.math.BigDecimal;
import java.util.*;

/**
 * Debit Card product with card-specific attributes.
 * 
 * Products: Debit Mastercard, girocard
 * 
 * Note: Debit cards are typically bundled with Giro Konto products,
 * but this domain model allows for standalone card information queries.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class DebitCardProduct extends Product {
    
    private BigDecimal dailyWithdrawalLimit;
    private BigDecimal dailyPurchaseLimit;
    private boolean contactlessEnabled = true;
    private Integer contactlessLimit; // Max amount without PIN, e.g., 50
    private boolean onlinePaymentsEnabled = true;
    private boolean applePaySupported = true;
    private boolean googlePaySupported = true;
    private String cardNetwork; // Mastercard, Maestro, girocard
    private Integer pinLength = 4;
    private boolean instantNotifications = true;
    private String bundledWithProductId; // Reference to parent Giro Konto
    
    private DebitCardProduct() {
        this.category = ProductCategory.DEBIT_CARD;
    }
    
    public BigDecimal getDailyWithdrawalLimit() { return dailyWithdrawalLimit; }
    public BigDecimal getDailyPurchaseLimit() { return dailyPurchaseLimit; }
    public boolean isContactlessEnabled() { return contactlessEnabled; }
    public Integer getContactlessLimit() { return contactlessLimit; }
    public boolean isOnlinePaymentsEnabled() { return onlinePaymentsEnabled; }
    public boolean isApplePaySupported() { return applePaySupported; }
    public boolean isGooglePaySupported() { return googlePaySupported; }
    public String getCardNetwork() { return cardNetwork; }
    public Integer getPinLength() { return pinLength; }
    public boolean isInstantNotifications() { return instantNotifications; }
    public String getBundledWithProductId() { return bundledWithProductId; }
    
    @Override
    public String formatForVoice(String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append(getName(lang)).append(": ");
        sb.append(getDescription(lang)).append(" ");
        
        if (dailyWithdrawalLimit != null) {
            sb.append("Daily ATM withdrawal limit: €").append(dailyWithdrawalLimit).append(". ");
        }
        
        if (dailyPurchaseLimit != null) {
            sb.append("Daily purchase limit: €").append(dailyPurchaseLimit).append(". ");
        }
        
        if (contactlessEnabled) {
            sb.append("Contactless payments enabled");
            if (contactlessLimit != null) {
                sb.append(" up to €").append(contactlessLimit).append(" without PIN");
            }
            sb.append(". ");
        }
        
        List<String> mobilePayments = new ArrayList<>();
        if (applePaySupported) mobilePayments.add("Apple Pay");
        if (googlePaySupported) mobilePayments.add("Google Pay");
        if (!mobilePayments.isEmpty()) {
            sb.append("Supports ").append(String.join(" and ", mobilePayments)).append(". ");
        }
        
        return sb.toString();
    }
    
    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = super.toMap();
        map.put("dailyWithdrawalLimit", dailyWithdrawalLimit);
        map.put("dailyPurchaseLimit", dailyPurchaseLimit);
        map.put("contactlessEnabled", contactlessEnabled);
        map.put("contactlessLimit", contactlessLimit);
        map.put("onlinePaymentsEnabled", onlinePaymentsEnabled);
        map.put("applePaySupported", applePaySupported);
        map.put("googlePaySupported", googlePaySupported);
        map.put("cardNetwork", cardNetwork);
        map.put("pinLength", pinLength);
        map.put("instantNotifications", instantNotifications);
        map.put("bundledWithProductId", bundledWithProductId);
        return map;
    }
    
    public static DebitCardBuilder debitCardBuilder() { return new DebitCardBuilder(); }
    
    public static class DebitCardBuilder extends Product.Builder {
        private final DebitCardProduct dcProduct;
        
        public DebitCardBuilder() {
            super(new DebitCardProduct());
            this.dcProduct = (DebitCardProduct) product;
        }
        
        public DebitCardBuilder dailyWithdrawalLimit(BigDecimal l) { dcProduct.dailyWithdrawalLimit = l; return this; }
        public DebitCardBuilder dailyWithdrawalLimit(double l) { dcProduct.dailyWithdrawalLimit = BigDecimal.valueOf(l); return this; }
        public DebitCardBuilder dailyPurchaseLimit(BigDecimal l) { dcProduct.dailyPurchaseLimit = l; return this; }
        public DebitCardBuilder dailyPurchaseLimit(double l) { dcProduct.dailyPurchaseLimit = BigDecimal.valueOf(l); return this; }
        public DebitCardBuilder contactlessEnabled(boolean c) { dcProduct.contactlessEnabled = c; return this; }
        public DebitCardBuilder contactlessLimit(Integer l) { dcProduct.contactlessLimit = l; return this; }
        public DebitCardBuilder onlinePaymentsEnabled(boolean o) { dcProduct.onlinePaymentsEnabled = o; return this; }
        public DebitCardBuilder applePaySupported(boolean a) { dcProduct.applePaySupported = a; return this; }
        public DebitCardBuilder googlePaySupported(boolean g) { dcProduct.googlePaySupported = g; return this; }
        public DebitCardBuilder cardNetwork(String n) { dcProduct.cardNetwork = n; return this; }
        public DebitCardBuilder pinLength(Integer p) { dcProduct.pinLength = p; return this; }
        public DebitCardBuilder instantNotifications(boolean i) { dcProduct.instantNotifications = i; return this; }
        public DebitCardBuilder bundledWithProductId(String b) { dcProduct.bundledWithProductId = b; return this; }
        
        // Re-expose parent builder methods with correct return type
        @Override public DebitCardBuilder id(String id) { super.id(id); return this; }
        @Override public DebitCardBuilder nameEn(String n) { super.nameEn(n); return this; }
        @Override public DebitCardBuilder nameDe(String n) { super.nameDe(n); return this; }
        @Override public DebitCardBuilder descriptionEn(String d) { super.descriptionEn(d); return this; }
        @Override public DebitCardBuilder descriptionDe(String d) { super.descriptionDe(d); return this; }
        @Override public DebitCardBuilder features(List<ProductFeature> f) { super.features(f); return this; }
        @Override public DebitCardBuilder addFeature(ProductFeature f) { super.addFeature(f); return this; }
        @Override public DebitCardBuilder feeSchedule(FeeSchedule f) { super.feeSchedule(f); return this; }
        @Override public DebitCardBuilder eligibility(EligibilityCriteria e) { super.eligibility(e); return this; }
        @Override public DebitCardBuilder termsAndConditionsUrl(String u) { super.termsAndConditionsUrl(u); return this; }
        @Override public DebitCardBuilder applicationUrl(String u) { super.applicationUrl(u); return this; }
        @Override public DebitCardBuilder isActive(boolean a) { super.isActive(a); return this; }
        @Override public DebitCardBuilder comparisonGroup(String g) { super.comparisonGroup(g); return this; }
        
        @Override
        public DebitCardProduct build() { return dcProduct; }
    }
}
