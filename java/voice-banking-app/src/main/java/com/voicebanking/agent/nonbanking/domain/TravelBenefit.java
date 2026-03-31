package com.voicebanking.agent.nonbanking.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Travel benefit model for travel-related non-banking services.
 * 
 * Includes airport lounges, Miles & More, concierge, priority pass, etc.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class TravelBenefit extends NonBankingService {
    
    private TravelBenefitType benefitType;
    private String accessInstructionsEn;
    private String accessInstructionsDe;
    private int annualAccessLimit;  // -1 = unlimited
    private String companionRulesEn;
    private String companionRulesDe;
    private String locationInfo;  // e.g., "1,300+ lounges worldwide"
    private String partnerAppName;
    private String partnerAppDeepLink;
    private String membershipIdRequired;  // e.g., "Priority Pass membership"

    protected TravelBenefit() {
        super();
        this.category = ServiceCategory.TRAVEL;
    }

    // Getters
    public TravelBenefitType getBenefitType() { return benefitType; }
    public String getAccessInstructionsEn() { return accessInstructionsEn; }
    public String getAccessInstructionsDe() { return accessInstructionsDe; }
    public int getAnnualAccessLimit() { return annualAccessLimit; }
    public String getCompanionRulesEn() { return companionRulesEn; }
    public String getCompanionRulesDe() { return companionRulesDe; }
    public String getLocationInfo() { return locationInfo; }
    public String getPartnerAppName() { return partnerAppName; }
    public String getPartnerAppDeepLink() { return partnerAppDeepLink; }
    public String getMembershipIdRequired() { return membershipIdRequired; }

    public String getAccessInstructions(String lang) {
        return "de".equalsIgnoreCase(lang) ? accessInstructionsDe : accessInstructionsEn;
    }

    public String getCompanionRules(String lang) {
        return "de".equalsIgnoreCase(lang) ? companionRulesDe : companionRulesEn;
    }

    public boolean hasUnlimitedAccess() {
        return annualAccessLimit < 0;
    }

    /**
     * Format travel benefit for voice output.
     */
    @Override
    public String formatForVoiceDetailed(String lang) {
        StringBuilder sb = new StringBuilder();
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        sb.append(getName(lang)).append(". ");
        sb.append(getDescriptionLong(lang)).append(" ");
        
        if (locationInfo != null) {
            sb.append(isGerman ? "Verfügbar: " : "Available: ");
            sb.append(locationInfo).append(". ");
        }
        
        String access = getAccessInstructions(lang);
        if (access != null) {
            sb.append(access).append(" ");
        }
        
        if (annualAccessLimit > 0) {
            sb.append(isGerman 
                ? "Sie haben " + annualAccessLimit + " kostenlose Besuche pro Jahr. "
                : "You have " + annualAccessLimit + " complimentary visits per year. ");
        } else if (hasUnlimitedAccess()) {
            sb.append(isGerman 
                ? "Unbegrenzter Zugang inklusive. " 
                : "Unlimited access included. ");
        }
        
        String companion = getCompanionRules(lang);
        if (companion != null) {
            sb.append(companion);
        }
        
        return sb.toString();
    }

    /**
     * Format access instructions for voice.
     */
    public String formatAccessForVoice(String lang) {
        StringBuilder sb = new StringBuilder();
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        sb.append(isGerman ? "Um " : "To access ");
        sb.append(getName(lang));
        sb.append(isGerman ? " zu nutzen: " : ": ");
        
        String access = getAccessInstructions(lang);
        if (access != null) {
            sb.append(access);
        }
        
        if (membershipIdRequired != null) {
            sb.append(" ");
            sb.append(isGerman 
                ? "Sie benötigen Ihre " 
                : "You will need your ");
            sb.append(membershipIdRequired).append(".");
        }
        
        if (partnerAppName != null) {
            sb.append(" ");
            sb.append(isGerman 
                ? "Sie können auch die " 
                : "You can also use the ");
            sb.append(partnerAppName);
            sb.append(isGerman ? " App verwenden." : " app.");
        }
        
        return sb.toString();
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = super.toMap();
        map.put("benefitType", benefitType != null ? benefitType.name() : null);
        map.put("accessInstructionsEn", accessInstructionsEn);
        map.put("accessInstructionsDe", accessInstructionsDe);
        map.put("annualAccessLimit", annualAccessLimit);
        map.put("unlimitedAccess", hasUnlimitedAccess());
        map.put("companionRulesEn", companionRulesEn);
        map.put("companionRulesDe", companionRulesDe);
        map.put("locationInfo", locationInfo);
        map.put("partnerAppName", partnerAppName);
        map.put("partnerAppDeepLink", partnerAppDeepLink);
        map.put("membershipIdRequired", membershipIdRequired);
        return map;
    }

    public static TravelBuilder travelBuilder() {
        return new TravelBuilder();
    }

    public static class TravelBuilder {
        private final TravelBenefit tb = new TravelBenefit();

        public TravelBuilder serviceId(String id) { tb.serviceId = id; return this; }
        public TravelBuilder nameEn(String name) { tb.nameEn = name; return this; }
        public TravelBuilder nameDe(String name) { tb.nameDe = name; return this; }
        public TravelBuilder benefitType(TravelBenefitType type) { tb.benefitType = type; return this; }
        public TravelBuilder descriptionShortEn(String desc) { tb.descriptionShortEn = desc; return this; }
        public TravelBuilder descriptionShortDe(String desc) { tb.descriptionShortDe = desc; return this; }
        public TravelBuilder descriptionLongEn(String desc) { tb.descriptionLongEn = desc; return this; }
        public TravelBuilder descriptionLongDe(String desc) { tb.descriptionLongDe = desc; return this; }
        public TravelBuilder eligibility(ServiceEligibility elig) { tb.eligibility = elig; return this; }
        public TravelBuilder accessInstructionsEn(String inst) { tb.accessInstructionsEn = inst; return this; }
        public TravelBuilder accessInstructionsDe(String inst) { tb.accessInstructionsDe = inst; return this; }
        public TravelBuilder annualAccessLimit(int limit) { tb.annualAccessLimit = limit; return this; }
        public TravelBuilder companionRulesEn(String rules) { tb.companionRulesEn = rules; return this; }
        public TravelBuilder companionRulesDe(String rules) { tb.companionRulesDe = rules; return this; }
        public TravelBuilder locationInfo(String info) { tb.locationInfo = info; return this; }
        public TravelBuilder partnerName(String name) { tb.partnerName = name; return this; }
        public TravelBuilder partnerPhone(String phone) { tb.partnerPhone = phone; return this; }
        public TravelBuilder partnerWebsite(String website) { tb.partnerWebsite = website; return this; }
        public TravelBuilder partnerAppName(String name) { tb.partnerAppName = name; return this; }
        public TravelBuilder partnerAppDeepLink(String link) { tb.partnerAppDeepLink = link; return this; }
        public TravelBuilder membershipIdRequired(String id) { tb.membershipIdRequired = id; return this; }
        public TravelBuilder termsUrl(String url) { tb.termsUrl = url; return this; }
        public TravelBuilder active(boolean active) { tb.active = active; return this; }

        public TravelBenefit build() {
            if (tb.serviceId == null) throw new IllegalStateException("serviceId is required");
            if (tb.nameEn == null) throw new IllegalStateException("nameEn is required");
            if (tb.benefitType == null) throw new IllegalStateException("benefitType is required");
            return tb;
        }
    }
}
