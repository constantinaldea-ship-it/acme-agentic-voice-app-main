package com.voicebanking.bfa.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * DTO for travel-related benefits.
 * 
 * <p>Provides information about travel benefits including airport lounges,
 * Miles & More program, travel concierge, and other travel perks.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-03
 */
@Schema(description = "Travel-related benefits")
public record TravelBenefitsDto(
    @Schema(description = "List of travel benefits")
    List<TravelBenefit> benefits,
    
    @Schema(description = "Number of travel benefits", example = "6")
    int count,
    
    @Schema(description = "Customer's card tier", example = "PLATINUM")
    String cardTier,
    
    @Schema(description = "Miles balance if Miles & More enrolled")
    MilesInfo milesInfo,
    
    @Schema(description = "Voice-friendly response")
    String voiceResponse,
    
    @Schema(description = "Regulatory disclaimer")
    String disclaimer
) {
    
    /**
     * Individual travel benefit.
     */
    @Schema(description = "Travel benefit details")
    public record TravelBenefit(
        @Schema(description = "Benefit type", example = "AIRPORT_LOUNGE")
        String type,
        
        @Schema(description = "Benefit name", example = "Priority Pass Lounge Access")
        String name,
        
        @Schema(description = "Benefit description")
        String description,
        
        @Schema(description = "Minimum card tier required", example = "PLATINUM")
        String minimumTier,
        
        @Schema(description = "Whether customer is eligible")
        boolean eligible,
        
        @Schema(description = "Eligibility message if not eligible")
        String eligibilityMessage,
        
        @Schema(description = "Number of free uses per year", example = "4")
        Integer freeUsesPerYear,
        
        @Schema(description = "Guest allowance", example = "1")
        Integer guestAllowance,
        
        @Schema(description = "Partner network name", example = "Priority Pass")
        String partnerNetwork,
        
        @Schema(description = "How to access/use this benefit")
        String accessInstructions,
        
        @Schema(description = "Related mobile app if any", example = "Priority Pass App")
        String relatedApp
    ) {
        /**
         * Factory method for eligible benefit.
         */
        public static TravelBenefit ofEligible(
                String type, String name, String description, String minimumTier,
                Integer freeUsesPerYear, Integer guestAllowance, String partnerNetwork,
                String accessInstructions, String relatedApp
        ) {
            return new TravelBenefit(
                type, name, description, minimumTier, true, null,
                freeUsesPerYear, guestAllowance, partnerNetwork, accessInstructions, relatedApp
            );
        }
        
        /**
         * Factory method for ineligible benefit.
         */
        public static TravelBenefit ofIneligible(
                String type, String name, String description, String minimumTier,
                String eligibilityMessage
        ) {
            return new TravelBenefit(
                type, name, description, minimumTier, false, eligibilityMessage,
                null, null, null, null, null
            );
        }
    }
    
    /**
     * Miles & More program information.
     */
    @Schema(description = "Miles & More program details")
    public record MilesInfo(
        @Schema(description = "Whether enrolled in Miles & More")
        boolean enrolled,
        
        @Schema(description = "Miles & More member ID")
        String memberId,
        
        @Schema(description = "Current miles balance", example = "45230")
        Integer currentBalance,
        
        @Schema(description = "Miles expiring within 6 months", example = "5000")
        Integer expiringMiles,
        
        @Schema(description = "Status level", example = "SENATOR")
        String statusLevel,
        
        @Schema(description = "Status miles for current year", example = "35000")
        Integer statusMiles
    ) {
        public static MilesInfo notEnrolled() {
            return new MilesInfo(false, null, null, null, null, null);
        }
        
        public static MilesInfo of(String memberId, int balance, int expiring, String status, int statusMiles) {
            return new MilesInfo(true, memberId, balance, expiring, status, statusMiles);
        }
    }
    
    /**
     * Travel benefit types.
     */
    public enum TravelBenefitType {
        AIRPORT_LOUNGE("Airport Lounge Access", "Flughafen-Lounge-Zugang"),
        MILES_AND_MORE("Miles & More Program", "Miles & More Programm"),
        TRAVEL_CONCIERGE("Travel Concierge", "Reise-Concierge"),
        FAST_TRACK("Fast Track Security", "Fast Track Sicherheit"),
        HOTEL_BENEFITS("Hotel Benefits", "Hotel-Vorteile"),
        CAR_RENTAL("Car Rental Benefits", "Mietwagen-Vorteile"),
        TRAVEL_INSURANCE("Travel Insurance", "Reiseversicherung");
        
        private final String nameEn;
        private final String nameDe;
        
        TravelBenefitType(String nameEn, String nameDe) {
            this.nameEn = nameEn;
            this.nameDe = nameDe;
        }
        
        public String getName(String language) {
            return "de".equalsIgnoreCase(language) ? nameDe : nameEn;
        }
    }
    
    private static final String DISCLAIMER_EN = "Travel benefits are subject to availability and terms of our partner networks. Please present your card at participating locations.";
    private static final String DISCLAIMER_DE = "Reisevorteile unterliegen der Verfügbarkeit und den Bedingungen unserer Partnernetzwerke. Bitte zeigen Sie Ihre Karte an teilnehmenden Standorten vor.";
    
    /**
     * Factory method to create TravelBenefitsDto.
     */
    public static TravelBenefitsDto of(
            List<TravelBenefit> benefits,
            String cardTier,
            MilesInfo milesInfo,
            String language
    ) {
        String voiceResponse = formatVoiceResponse(benefits, cardTier, milesInfo, language);
        String disclaimer = "de".equalsIgnoreCase(language) ? DISCLAIMER_DE : DISCLAIMER_EN;
        
        return new TravelBenefitsDto(
            benefits,
            benefits.size(),
            cardTier,
            milesInfo,
            voiceResponse,
            disclaimer
        );
    }
    
    private static String formatVoiceResponse(
            List<TravelBenefit> benefits, String cardTier, MilesInfo milesInfo, String language
    ) {
        int eligible = (int) benefits.stream().filter(TravelBenefit::eligible).count();
        
        StringBuilder sb = new StringBuilder();
        
        if ("de".equalsIgnoreCase(language)) {
            sb.append(String.format("Mit Ihrer %s-Karte haben Sie Zugang zu %d Reisevorteilen. ", cardTier, eligible));
            
            if (milesInfo != null && milesInfo.enrolled()) {
                sb.append(String.format("Ihr Miles & More Guthaben beträgt %d Meilen. ", milesInfo.currentBalance()));
                if (milesInfo.expiringMiles() != null && milesInfo.expiringMiles() > 0) {
                    sb.append(String.format("%d Meilen verfallen in den nächsten 6 Monaten. ", milesInfo.expiringMiles()));
                }
            }
            
            sb.append("Möchten Sie Details zu einem bestimmten Vorteil?");
        } else {
            sb.append(String.format("With your %s card, you have access to %d travel benefits. ", cardTier, eligible));
            
            if (milesInfo != null && milesInfo.enrolled()) {
                sb.append(String.format("Your Miles & More balance is %d miles. ", milesInfo.currentBalance()));
                if (milesInfo.expiringMiles() != null && milesInfo.expiringMiles() > 0) {
                    sb.append(String.format("%d miles are expiring within 6 months. ", milesInfo.expiringMiles()));
                }
            }
            
            sb.append("Would you like details on a specific benefit?");
        }
        
        return sb.toString();
    }
}
