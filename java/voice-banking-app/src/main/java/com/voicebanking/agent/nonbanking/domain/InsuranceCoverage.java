package com.voicebanking.agent.nonbanking.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Insurance coverage model for insurance-related non-banking services.
 * 
 * Includes travel insurance, purchase protection, liability coverage, etc.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class InsuranceCoverage extends NonBankingService {
    
    private InsuranceType insuranceType;
    private double coverageLimit;
    private String coverageLimitFormatted;
    private String coverageSummaryEn;
    private String coverageSummaryDe;
    private List<String> exclusionsEn = new ArrayList<>();
    private List<String> exclusionsDe = new ArrayList<>();
    private String claimProcessEn;
    private String claimProcessDe;
    private String claimPhone;
    private String claimEmail;
    private int deductibleAmount;
    private String validityPeriod;

    protected InsuranceCoverage() {
        super();
        this.category = ServiceCategory.INSURANCE;
    }

    // Getters
    public InsuranceType getInsuranceType() { return insuranceType; }
    public double getCoverageLimit() { return coverageLimit; }
    public String getCoverageLimitFormatted() { return coverageLimitFormatted; }
    public String getCoverageSummaryEn() { return coverageSummaryEn; }
    public String getCoverageSummaryDe() { return coverageSummaryDe; }
    public List<String> getExclusionsEn() { return List.copyOf(exclusionsEn); }
    public List<String> getExclusionsDe() { return List.copyOf(exclusionsDe); }
    public String getClaimProcessEn() { return claimProcessEn; }
    public String getClaimProcessDe() { return claimProcessDe; }
    public String getClaimPhone() { return claimPhone; }
    public String getClaimEmail() { return claimEmail; }
    public int getDeductibleAmount() { return deductibleAmount; }
    public String getValidityPeriod() { return validityPeriod; }

    public String getCoverageSummary(String lang) {
        return "de".equalsIgnoreCase(lang) ? coverageSummaryDe : coverageSummaryEn;
    }

    public List<String> getExclusions(String lang) {
        return "de".equalsIgnoreCase(lang) ? List.copyOf(exclusionsDe) : List.copyOf(exclusionsEn);
    }

    public String getClaimProcess(String lang) {
        return "de".equalsIgnoreCase(lang) ? claimProcessDe : claimProcessEn;
    }

    /**
     * Format insurance for voice output.
     */
    @Override
    public String formatForVoiceDetailed(String lang) {
        StringBuilder sb = new StringBuilder();
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        sb.append(getName(lang)).append(". ");
        sb.append(getCoverageSummary(lang)).append(" ");
        
        if (coverageLimitFormatted != null) {
            sb.append(isGerman ? "Deckungssumme: " : "Coverage limit: ");
            sb.append(coverageLimitFormatted).append(". ");
        }
        
        if (deductibleAmount > 0) {
            sb.append(isGerman ? "Selbstbeteiligung: €" : "Deductible: €");
            sb.append(deductibleAmount).append(". ");
        }
        
        String process = getClaimProcess(lang);
        if (process != null) {
            sb.append(process).append(" ");
        }
        
        if (claimPhone != null) {
            sb.append(isGerman 
                ? "Bei Schadensfällen kontaktieren Sie " 
                : "For claims, contact ");
            sb.append(partnerName).append(isGerman ? " unter " : " at ");
            sb.append(claimPhone).append(".");
        }
        
        return sb.toString();
    }

    /**
     * Format claim contact for voice.
     */
    public String formatClaimContactForVoice(String lang) {
        boolean isGerman = "de".equalsIgnoreCase(lang);
        StringBuilder sb = new StringBuilder();
        
        sb.append(isGerman 
            ? "Um einen Schadensfall zu melden für " 
            : "To file a claim for ");
        sb.append(getName(lang)).append(", ");
        sb.append(isGerman ? "rufen Sie " : "call ");
        sb.append(partnerName);
        
        if (claimPhone != null) {
            sb.append(isGerman ? " unter " : " at ").append(claimPhone);
        }
        
        sb.append(". ");
        
        if (claimEmail != null) {
            sb.append(isGerman ? "Oder per E-Mail: " : "Or email: ");
            sb.append(claimEmail).append(".");
        }
        
        return sb.toString();
    }

    @Override
    public Map<String, Object> toMap() {
        Map<String, Object> map = super.toMap();
        map.put("insuranceType", insuranceType != null ? insuranceType.name() : null);
        map.put("coverageLimit", coverageLimit);
        map.put("coverageLimitFormatted", coverageLimitFormatted);
        map.put("coverageSummaryEn", coverageSummaryEn);
        map.put("coverageSummaryDe", coverageSummaryDe);
        map.put("exclusionsEn", exclusionsEn);
        map.put("exclusionsDe", exclusionsDe);
        map.put("claimProcessEn", claimProcessEn);
        map.put("claimProcessDe", claimProcessDe);
        map.put("claimPhone", claimPhone);
        map.put("claimEmail", claimEmail);
        map.put("deductibleAmount", deductibleAmount);
        map.put("validityPeriod", validityPeriod);
        return map;
    }

    public static InsuranceBuilder insuranceBuilder() {
        return new InsuranceBuilder();
    }

    public static class InsuranceBuilder {
        private final InsuranceCoverage ins = new InsuranceCoverage();

        public InsuranceBuilder serviceId(String id) { ins.serviceId = id; return this; }
        public InsuranceBuilder nameEn(String name) { ins.nameEn = name; return this; }
        public InsuranceBuilder nameDe(String name) { ins.nameDe = name; return this; }
        public InsuranceBuilder insuranceType(InsuranceType type) { ins.insuranceType = type; return this; }
        public InsuranceBuilder descriptionShortEn(String desc) { ins.descriptionShortEn = desc; return this; }
        public InsuranceBuilder descriptionShortDe(String desc) { ins.descriptionShortDe = desc; return this; }
        public InsuranceBuilder descriptionLongEn(String desc) { ins.descriptionLongEn = desc; return this; }
        public InsuranceBuilder descriptionLongDe(String desc) { ins.descriptionLongDe = desc; return this; }
        public InsuranceBuilder eligibility(ServiceEligibility elig) { ins.eligibility = elig; return this; }
        public InsuranceBuilder coverageLimit(double limit) { ins.coverageLimit = limit; return this; }
        public InsuranceBuilder coverageLimitFormatted(String formatted) { ins.coverageLimitFormatted = formatted; return this; }
        public InsuranceBuilder coverageSummaryEn(String summary) { ins.coverageSummaryEn = summary; return this; }
        public InsuranceBuilder coverageSummaryDe(String summary) { ins.coverageSummaryDe = summary; return this; }
        public InsuranceBuilder exclusionsEn(List<String> exclusions) { ins.exclusionsEn = new ArrayList<>(exclusions); return this; }
        public InsuranceBuilder exclusionsDe(List<String> exclusions) { ins.exclusionsDe = new ArrayList<>(exclusions); return this; }
        public InsuranceBuilder claimProcessEn(String process) { ins.claimProcessEn = process; return this; }
        public InsuranceBuilder claimProcessDe(String process) { ins.claimProcessDe = process; return this; }
        public InsuranceBuilder claimPhone(String phone) { ins.claimPhone = phone; return this; }
        public InsuranceBuilder claimEmail(String email) { ins.claimEmail = email; return this; }
        public InsuranceBuilder deductibleAmount(int amount) { ins.deductibleAmount = amount; return this; }
        public InsuranceBuilder validityPeriod(String period) { ins.validityPeriod = period; return this; }
        public InsuranceBuilder partnerName(String name) { ins.partnerName = name; return this; }
        public InsuranceBuilder partnerPhone(String phone) { ins.partnerPhone = phone; return this; }
        public InsuranceBuilder partnerWebsite(String website) { ins.partnerWebsite = website; return this; }
        public InsuranceBuilder termsUrl(String url) { ins.termsUrl = url; return this; }
        public InsuranceBuilder active(boolean active) { ins.active = active; return this; }

        public InsuranceCoverage build() {
            if (ins.serviceId == null) throw new IllegalStateException("serviceId is required");
            if (ins.nameEn == null) throw new IllegalStateException("nameEn is required");
            if (ins.insuranceType == null) throw new IllegalStateException("insuranceType is required");
            return ins;
        }
    }
}
