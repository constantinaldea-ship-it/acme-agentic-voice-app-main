package com.voicebanking.agent.nonbanking.domain;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * Partner offer model for promotional offers from Acme Bank partners.
 * 
 * Includes retail discounts, cashback, exclusive access, etc.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class PartnerOffer extends NonBankingService {
    
    private OfferType offerType;
    private String discountAmount;  // e.g., "10%", "€20"
    private double discountPercentage;  // For sorting/filtering
    private String redemptionCode;
    private String redemptionInstructionsEn;
    private String redemptionInstructionsDe;
    private LocalDate validFrom;
    private LocalDate validUntil;
    private String onlineUrl;
    private boolean onlineOnly;
    private boolean storeOnly;
    private String locationRestriction;  // e.g., "Germany only"

    protected PartnerOffer() {
        super();
        this.category = ServiceCategory.PARTNERS;
    }

    // Getters
    public OfferType getOfferType() { return offerType; }
    public String getDiscountAmount() { return discountAmount; }
    public double getDiscountPercentage() { return discountPercentage; }
    public String getRedemptionCode() { return redemptionCode; }
    public String getRedemptionInstructionsEn() { return redemptionInstructionsEn; }
    public String getRedemptionInstructionsDe() { return redemptionInstructionsDe; }
    public LocalDate getValidFrom() { return validFrom; }
    public LocalDate getValidUntil() { return validUntil; }
    public String getOnlineUrl() { return onlineUrl; }
    public boolean isOnlineOnly() { return onlineOnly; }
    public boolean isStoreOnly() { return storeOnly; }
    public String getLocationRestriction() { return locationRestriction; }

    public String getRedemptionInstructions(String lang) {
        return "de".equalsIgnoreCase(lang) ? redemptionInstructionsDe : redemptionInstructionsEn;
    }

    /**
     * Check if offer is currently valid.
     */
    public boolean isCurrentlyValid() {
        LocalDate today = LocalDate.now();
        if (validFrom != null && today.isBefore(validFrom)) {
            return false;
        }
        return validUntil == null || !today.isAfter(validUntil);
    }

    /**
     * Get days until expiration.
     */
    public long getDaysUntilExpiration() {
        if (validUntil == null) return Long.MAX_VALUE;
        return java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), validUntil);
    }

    /**
     * Check if offer is expiring soon (within 7 days).
     */
    public boolean isExpiringSoon() {
        long days = getDaysUntilExpiration();
        return days >= 0 && days <= 7;
    }

    /**
     * Format offer for voice output.
     */
    @Override
    public String formatForVoiceDetailed(String lang) {
        StringBuilder sb = new StringBuilder();
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        // Partner and discount
        if (discountAmount != null) {
            sb.append(discountAmount);
            sb.append(isGerman ? " Rabatt bei " : " discount at ");
            sb.append(partnerName).append(". ");
        } else {
            sb.append(getName(lang)).append(" ");
            sb.append(isGerman ? "bei " : "at ").append(partnerName).append(". ");
        }
        
        sb.append(getDescriptionShort(lang)).append(" ");
        
        // Validity
        if (validUntil != null) {
            if (isExpiringSoon()) {
                long days = getDaysUntilExpiration();
                sb.append(isGerman 
                    ? "Angebot endet in " + days + " Tagen. " 
                    : "Offer ends in " + days + " days. ");
            } else {
                sb.append(isGerman 
                    ? "Gültig bis " 
                    : "Valid through ");
                sb.append(validUntil.toString()).append(". ");
            }
        }
        
        // Redemption
        String redemption = getRedemptionInstructions(lang);
        if (redemption != null) {
            sb.append(redemption);
        } else if (redemptionCode != null) {
            sb.append(isGerman 
                ? "Verwenden Sie den Code " 
                : "Use code ");
            sb.append(redemptionCode);
            if (onlineOnly) {
                sb.append(isGerman ? " beim Online-Checkout." : " at online checkout.");
            } else {
                sb.append(".");
            }
        }
        
        return sb.toString();
    }

    /**
     * Format brief offer for listing.
     */
    public String formatForVoiceBrief(String lang) {
        StringBuilder sb = new StringBuilder();
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        if (discountAmount != null) {
            sb.append(discountAmount);
            sb.append(isGerman ? " bei " : " at ");
        }
        sb.append(partnerName);
        
        if (isExpiringSoon()) {
            sb.append(isGerman ? " - bald ablaufend" : " - expiring soon");
        }
        
        return sb.toString();
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = super.toMap();
        map.put("offerType", offerType != null ? offerType.name() : null);
        map.put("discountAmount", discountAmount);
        map.put("discountPercentage", discountPercentage);
        map.put("redemptionCode", redemptionCode);
        map.put("redemptionInstructionsEn", redemptionInstructionsEn);
        map.put("redemptionInstructionsDe", redemptionInstructionsDe);
        map.put("validFrom", validFrom != null ? validFrom.toString() : null);
        map.put("validUntil", validUntil != null ? validUntil.toString() : null);
        map.put("isCurrentlyValid", isCurrentlyValid());
        map.put("isExpiringSoon", isExpiringSoon());
        map.put("daysUntilExpiration", getDaysUntilExpiration());
        map.put("onlineUrl", onlineUrl);
        map.put("onlineOnly", onlineOnly);
        map.put("storeOnly", storeOnly);
        map.put("locationRestriction", locationRestriction);
        return map;
    }

    public static OfferBuilder offerBuilder() {
        return new OfferBuilder();
    }

    public static class OfferBuilder {
        private final PartnerOffer offer = new PartnerOffer();

        public OfferBuilder serviceId(String id) { offer.serviceId = id; return this; }
        public OfferBuilder nameEn(String name) { offer.nameEn = name; return this; }
        public OfferBuilder nameDe(String name) { offer.nameDe = name; return this; }
        public OfferBuilder offerType(OfferType type) { offer.offerType = type; return this; }
        public OfferBuilder descriptionShortEn(String desc) { offer.descriptionShortEn = desc; return this; }
        public OfferBuilder descriptionShortDe(String desc) { offer.descriptionShortDe = desc; return this; }
        public OfferBuilder eligibility(ServiceEligibility elig) { offer.eligibility = elig; return this; }
        public OfferBuilder discountAmount(String amount) { offer.discountAmount = amount; return this; }
        public OfferBuilder discountPercentage(double pct) { offer.discountPercentage = pct; return this; }
        public OfferBuilder redemptionCode(String code) { offer.redemptionCode = code; return this; }
        public OfferBuilder redemptionInstructionsEn(String inst) { offer.redemptionInstructionsEn = inst; return this; }
        public OfferBuilder redemptionInstructionsDe(String inst) { offer.redemptionInstructionsDe = inst; return this; }
        public OfferBuilder validFrom(LocalDate date) { offer.validFrom = date; return this; }
        public OfferBuilder validUntil(LocalDate date) { offer.validUntil = date; return this; }
        public OfferBuilder partnerName(String name) { offer.partnerName = name; return this; }
        public OfferBuilder partnerWebsite(String website) { offer.partnerWebsite = website; return this; }
        public OfferBuilder onlineUrl(String url) { offer.onlineUrl = url; return this; }
        public OfferBuilder onlineOnly(boolean online) { offer.onlineOnly = online; return this; }
        public OfferBuilder storeOnly(boolean store) { offer.storeOnly = store; return this; }
        public OfferBuilder locationRestriction(String restriction) { offer.locationRestriction = restriction; return this; }
        public OfferBuilder termsUrl(String url) { offer.termsUrl = url; return this; }
        public OfferBuilder active(boolean active) { offer.active = active; return this; }

        public PartnerOffer build() {
            if (offer.serviceId == null) throw new IllegalStateException("serviceId is required");
            if (offer.nameEn == null) throw new IllegalStateException("nameEn is required");
            if (offer.partnerName == null) throw new IllegalStateException("partnerName is required");
            return offer;
        }
    }
}
