package com.voicebanking.bfa.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO for insurance coverage information.
 * 
 * <p>Provides detailed information about insurance benefits including
 * coverage limits, eligibility, and claim contact information.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-03
 */
@Schema(description = "Insurance coverage information")
public record InsuranceInfoDto(
    @Schema(description = "List of insurance coverages")
    List<InsuranceCoverage> insurances,
    
    @Schema(description = "Number of insurance products", example = "4")
    int count,
    
    @Schema(description = "Customer's card tier", example = "PLATINUM")
    String cardTier,
    
    @Schema(description = "Voice-friendly response")
    String voiceResponse,
    
    @Schema(description = "Currency for coverage amounts", example = "EUR")
    String currency,
    
    @Schema(description = "Regulatory disclaimer")
    String disclaimer
) {
    
    /**
     * Individual insurance coverage.
     */
    @Schema(description = "Insurance coverage details")
    public record InsuranceCoverage(
        @Schema(description = "Insurance type", example = "TRAVEL_MEDICAL")
        String type,
        
        @Schema(description = "Insurance type display name", example = "Travel Medical Insurance")
        String name,
        
        @Schema(description = "Coverage description")
        String description,
        
        @Schema(description = "Maximum coverage amount", example = "1000000.00")
        BigDecimal coverageLimit,
        
        @Schema(description = "What's included in the coverage")
        List<String> includes,
        
        @Schema(description = "What's excluded from coverage")
        List<String> excludes,
        
        @Schema(description = "Minimum card tier required", example = "GOLD")
        String minimumTier,
        
        @Schema(description = "Whether customer is eligible")
        boolean eligible,
        
        @Schema(description = "Eligibility message if not eligible")
        String eligibilityMessage,
        
        @Schema(description = "Insurance partner name", example = "AXA Insurance")
        String partnerName,
        
        @Schema(description = "Claim contact information")
        ClaimContact claimContact
    ) {
        /**
         * Factory method for eligible insurance.
         */
        public static InsuranceCoverage ofEligible(
                String type, String name, String description,
                BigDecimal coverageLimit, List<String> includes, List<String> excludes,
                String minimumTier, String partnerName, ClaimContact claimContact
        ) {
            return new InsuranceCoverage(
                type, name, description, coverageLimit, includes, excludes,
                minimumTier, true, null, partnerName, claimContact
            );
        }
        
        /**
         * Factory method for ineligible insurance.
         */
        public static InsuranceCoverage ofIneligible(
                String type, String name, String description,
                BigDecimal coverageLimit, List<String> includes, List<String> excludes,
                String minimumTier, String eligibilityMessage, String partnerName
        ) {
            return new InsuranceCoverage(
                type, name, description, coverageLimit, includes, excludes,
                minimumTier, false, eligibilityMessage, partnerName, null
            );
        }
    }
    
    /**
     * Claim contact information.
     */
    @Schema(description = "Contact information for filing claims")
    public record ClaimContact(
        @Schema(description = "24/7 emergency phone", example = "+49 800 100-2500")
        String emergencyPhone,
        
        @Schema(description = "Email for claims", example = "claims@axa.de")
        String email,
        
        @Schema(description = "Online portal URL")
        String portalUrl,
        
        @Schema(description = "Available hours", example = "24/7")
        String availability,
        
        @Schema(description = "Required documents for claims")
        List<String> requiredDocuments
    ) {}
    
    /**
     * Insurance types enumeration.
     */
    public enum InsuranceType {
        TRAVEL_MEDICAL("Travel Medical Insurance", "Reisekrankenversicherung"),
        TRIP_CANCELLATION("Trip Cancellation Insurance", "Reiserücktrittsversicherung"),
        PURCHASE_PROTECTION("Purchase Protection", "Einkaufsschutz"),
        RENTAL_CAR_CDW("Rental Car CDW", "Mietwagen-Vollkasko"),
        LUGGAGE_DELAY("Luggage Delay Insurance", "Gepäckverspätungsversicherung"),
        FLIGHT_DELAY("Flight Delay Insurance", "Flugverspätungsversicherung");
        
        private final String nameEn;
        private final String nameDe;
        
        InsuranceType(String nameEn, String nameDe) {
            this.nameEn = nameEn;
            this.nameDe = nameDe;
        }
        
        public String getName(String language) {
            return "de".equalsIgnoreCase(language) ? nameDe : nameEn;
        }
    }
    
    private static final String DISCLAIMER_EN = "Insurance coverage is provided by our partner insurers. Terms and conditions apply. Please refer to your policy documents for complete details.";
    private static final String DISCLAIMER_DE = "Der Versicherungsschutz wird von unseren Partnerversicherern erbracht. Es gelten die Allgemeinen Geschäftsbedingungen. Bitte beachten Sie Ihre Policendokumente für vollständige Details.";
    
    /**
     * Factory method to create InsuranceInfoDto.
     */
    public static InsuranceInfoDto of(
            List<InsuranceCoverage> insurances,
            String cardTier,
            String language
    ) {
        String voiceResponse = formatVoiceResponse(insurances, cardTier, language);
        String disclaimer = "de".equalsIgnoreCase(language) ? DISCLAIMER_DE : DISCLAIMER_EN;
        
        return new InsuranceInfoDto(
            insurances,
            insurances.size(),
            cardTier,
            voiceResponse,
            "EUR",
            disclaimer
        );
    }
    
    /**
     * Factory method for single insurance details.
     */
    public static InsuranceInfoDto ofSingle(
            InsuranceCoverage insurance,
            String cardTier,
            String language
    ) {
        String voiceResponse = formatSingleVoiceResponse(insurance, language);
        String disclaimer = "de".equalsIgnoreCase(language) ? DISCLAIMER_DE : DISCLAIMER_EN;
        
        return new InsuranceInfoDto(
            List.of(insurance),
            1,
            cardTier,
            voiceResponse,
            "EUR",
            disclaimer
        );
    }
    
    private static String formatVoiceResponse(List<InsuranceCoverage> insurances, String cardTier, String language) {
        int eligible = (int) insurances.stream().filter(InsuranceCoverage::eligible).count();
        
        if ("de".equalsIgnoreCase(language)) {
            return String.format(
                "Mit Ihrer %s-Karte haben Sie Zugang zu %d Versicherungsleistungen. Davon sind %d für Sie verfügbar. Möchten Sie Details zu einer bestimmten Versicherung?",
                cardTier, insurances.size(), eligible
            );
        } else {
            return String.format(
                "With your %s card, you have access to %d insurance products. %d are available to you. Would you like details on a specific insurance?",
                cardTier, insurances.size(), eligible
            );
        }
    }
    
    private static String formatSingleVoiceResponse(InsuranceCoverage insurance, String language) {
        if (!insurance.eligible()) {
            return insurance.eligibilityMessage();
        }
        
        if ("de".equalsIgnoreCase(language)) {
            return String.format(
                "Ihre %s bietet Deckung bis zu %s Euro. %s Für Schadensmeldungen kontaktieren Sie %s unter %s.",
                insurance.name(),
                insurance.coverageLimit() != null ? insurance.coverageLimit().toPlainString() : "unbegrenzt",
                insurance.description(),
                insurance.partnerName(),
                insurance.claimContact() != null ? insurance.claimContact().emergencyPhone() : "Ihren Kundenberater"
            );
        } else {
            return String.format(
                "Your %s provides coverage up to %s euros. %s To file a claim, contact %s at %s.",
                insurance.name(),
                insurance.coverageLimit() != null ? insurance.coverageLimit().toPlainString() : "unlimited",
                insurance.description(),
                insurance.partnerName(),
                insurance.claimContact() != null ? insurance.claimContact().emergencyPhone() : "your account manager"
            );
        }
    }
}
