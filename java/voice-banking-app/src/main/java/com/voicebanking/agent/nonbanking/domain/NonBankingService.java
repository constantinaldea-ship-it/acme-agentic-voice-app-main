package com.voicebanking.agent.nonbanking.domain;

import java.util.HashMap;
import java.util.Map;

/**
 * Base model for non-banking services offered by Acme Bank.
 * 
 * Represents third-party and ancillary services including insurance,
 * travel benefits, lifestyle perks, and partner offers.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class NonBankingService {
    
    protected String serviceId;
    protected String nameEn;
    protected String nameDe;
    protected ServiceCategory category;
    protected String descriptionShortEn;
    protected String descriptionShortDe;
    protected String descriptionLongEn;
    protected String descriptionLongDe;
    protected ServiceEligibility eligibility;
    protected String howToAccessEn;
    protected String howToAccessDe;
    protected String partnerName;
    protected String partnerContact;
    protected String partnerPhone;
    protected String partnerWebsite;
    protected String termsUrl;
    protected boolean active = true;

    protected NonBankingService() {}

    // Getters
    public String getServiceId() { return serviceId; }
    public String getNameEn() { return nameEn; }
    public String getNameDe() { return nameDe; }
    public ServiceCategory getCategory() { return category; }
    public String getDescriptionShortEn() { return descriptionShortEn; }
    public String getDescriptionShortDe() { return descriptionShortDe; }
    public String getDescriptionLongEn() { return descriptionLongEn; }
    public String getDescriptionLongDe() { return descriptionLongDe; }
    public ServiceEligibility getEligibility() { return eligibility; }
    public String getHowToAccessEn() { return howToAccessEn; }
    public String getHowToAccessDe() { return howToAccessDe; }
    public String getPartnerName() { return partnerName; }
    public String getPartnerContact() { return partnerContact; }
    public String getPartnerPhone() { return partnerPhone; }
    public String getPartnerWebsite() { return partnerWebsite; }
    public String getTermsUrl() { return termsUrl; }
    public boolean isActive() { return active; }

    /**
     * Get name in specified language.
     */
    public String getName(String lang) {
        return "de".equalsIgnoreCase(lang) ? nameDe : nameEn;
    }

    /**
     * Get short description in specified language.
     */
    public String getDescriptionShort(String lang) {
        return "de".equalsIgnoreCase(lang) ? descriptionShortDe : descriptionShortEn;
    }

    /**
     * Get full description in specified language.
     */
    public String getDescriptionLong(String lang) {
        return "de".equalsIgnoreCase(lang) ? descriptionLongDe : descriptionLongEn;
    }

    /**
     * Get how to access instructions in specified language.
     */
    public String getHowToAccess(String lang) {
        return "de".equalsIgnoreCase(lang) ? howToAccessDe : howToAccessEn;
    }

    /**
     * Format service for brief voice output.
     */
    public String formatForVoiceBrief(String lang) {
        return getName(lang) + ": " + getDescriptionShort(lang);
    }

    /**
     * Format service for detailed voice output.
     */
    public String formatForVoiceDetailed(String lang) {
        StringBuilder sb = new StringBuilder();
        sb.append(getName(lang)).append(". ");
        sb.append(getDescriptionLong(lang)).append(" ");
        
        String howTo = getHowToAccess(lang);
        if (howTo != null && !howTo.isBlank()) {
            sb.append(howTo).append(" ");
        }
        
        if (partnerPhone != null) {
            boolean isGerman = "de".equalsIgnoreCase(lang);
            sb.append(isGerman ? "Für weitere Informationen kontaktieren Sie " 
                              : "For more information, contact ");
            sb.append(partnerName).append(" at ").append(partnerPhone).append(".");
        }
        
        return sb.toString();
    }

    /**
     * Format contact information for voice.
     */
    public String formatContactForVoice(String lang) {
        if (partnerName == null) {
            return "de".equalsIgnoreCase(lang) 
                ? "Kontaktinformationen nicht verfügbar."
                : "Contact information not available.";
        }
        
        StringBuilder sb = new StringBuilder();
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        sb.append(isGerman ? "Für " : "For ").append(getName(lang));
        sb.append(isGerman ? ", kontaktieren Sie " : ", contact ");
        sb.append(partnerName);
        
        if (partnerPhone != null) {
            sb.append(isGerman ? " unter " : " at ").append(partnerPhone);
        }
        
        if (partnerWebsite != null) {
            sb.append(isGerman ? ", oder besuchen Sie " : ", or visit ").append(partnerWebsite);
        }
        
        sb.append(".");
        return sb.toString();
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("serviceId", serviceId);
        map.put("nameEn", nameEn);
        map.put("nameDe", nameDe);
        map.put("category", category != null ? category.name() : null);
        map.put("descriptionShortEn", descriptionShortEn);
        map.put("descriptionShortDe", descriptionShortDe);
        map.put("descriptionLongEn", descriptionLongEn);
        map.put("descriptionLongDe", descriptionLongDe);
        map.put("eligibility", eligibility != null ? eligibility.toMap() : null);
        map.put("howToAccessEn", howToAccessEn);
        map.put("howToAccessDe", howToAccessDe);
        map.put("partnerName", partnerName);
        map.put("partnerContact", partnerContact);
        map.put("partnerPhone", partnerPhone);
        map.put("partnerWebsite", partnerWebsite);
        map.put("termsUrl", termsUrl);
        map.put("active", active);
        return map;
    }

    public Map<String, Object> toBriefMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("serviceId", serviceId);
        map.put("nameEn", nameEn);
        map.put("nameDe", nameDe);
        map.put("category", category != null ? category.name() : null);
        map.put("descriptionShort", descriptionShortEn);
        return map;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final NonBankingService service = new NonBankingService();

        public Builder serviceId(String id) { service.serviceId = id; return this; }
        public Builder nameEn(String name) { service.nameEn = name; return this; }
        public Builder nameDe(String name) { service.nameDe = name; return this; }
        public Builder category(ServiceCategory cat) { service.category = cat; return this; }
        public Builder descriptionShortEn(String desc) { service.descriptionShortEn = desc; return this; }
        public Builder descriptionShortDe(String desc) { service.descriptionShortDe = desc; return this; }
        public Builder descriptionLongEn(String desc) { service.descriptionLongEn = desc; return this; }
        public Builder descriptionLongDe(String desc) { service.descriptionLongDe = desc; return this; }
        public Builder eligibility(ServiceEligibility elig) { service.eligibility = elig; return this; }
        public Builder howToAccessEn(String how) { service.howToAccessEn = how; return this; }
        public Builder howToAccessDe(String how) { service.howToAccessDe = how; return this; }
        public Builder partnerName(String name) { service.partnerName = name; return this; }
        public Builder partnerContact(String contact) { service.partnerContact = contact; return this; }
        public Builder partnerPhone(String phone) { service.partnerPhone = phone; return this; }
        public Builder partnerWebsite(String website) { service.partnerWebsite = website; return this; }
        public Builder termsUrl(String url) { service.termsUrl = url; return this; }
        public Builder active(boolean active) { service.active = active; return this; }

        public NonBankingService build() {
            if (service.serviceId == null) throw new IllegalStateException("serviceId is required");
            if (service.nameEn == null) throw new IllegalStateException("nameEn is required");
            if (service.category == null) throw new IllegalStateException("category is required");
            return service;
        }
    }
}
