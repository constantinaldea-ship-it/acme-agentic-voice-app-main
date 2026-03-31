package com.voicebanking.bfa.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DTO for customer benefits based on card tier.
 * 
 * <p>Returns all benefits available to the customer organized by category,
 * with upgrade suggestions when applicable.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-03
 */
@Schema(description = "Customer benefits based on card tier")
public record BenefitsDto(
    @Schema(description = "Customer's card tier", example = "PLATINUM")
    String cardTier,
    
    @Schema(description = "Total number of available benefits", example = "20")
    int totalCount,
    
    @Schema(description = "Benefits organized by category")
    Map<String, List<BenefitItem>> benefitsByCategory,
    
    @Schema(description = "Flat list of all benefits")
    List<BenefitItem> allBenefits,
    
    @Schema(description = "Summary counts per category")
    CategorySummary categorySummary,
    
    @Schema(description = "Upgrade suggestion if applicable")
    String upgradeSuggestion,
    
    @Schema(description = "Voice-friendly response")
    String voiceResponse,
    
    @Schema(description = "Currency for monetary values", example = "EUR")
    String currency,
    
    @Schema(description = "Regulatory disclaimer")
    String disclaimer
) {
    
    /**
     * Individual benefit item.
     */
    @Schema(description = "Individual benefit")
    public record BenefitItem(
        @Schema(description = "Unique benefit ID", example = "INS-TRAVEL-001")
        String id,
        
        @Schema(description = "Benefit name", example = "Travel Medical Insurance")
        String name,
        
        @Schema(description = "Benefit description")
        String description,
        
        @Schema(description = "Category: INSURANCE, TRAVEL, LIFESTYLE, PARTNERS, DIGITAL")
        String category,
        
        @Schema(description = "Minimum card tier required", example = "GOLD")
        String minimumTier,
        
        @Schema(description = "Whether customer is eligible based on their tier")
        boolean eligible,
        
        @Schema(description = "Value or coverage amount if applicable", example = "1000000.00")
        String value,
        
        @Schema(description = "Partner name if applicable", example = "AXA Insurance")
        String partnerName
    ) {}
    
    /**
     * Summary of benefits per category.
     */
    @Schema(description = "Counts of benefits per category")
    public record CategorySummary(
        @Schema(description = "Number of insurance benefits", example = "4")
        int insurance,
        
        @Schema(description = "Number of travel benefits", example = "6")
        int travel,
        
        @Schema(description = "Number of lifestyle benefits", example = "3")
        int lifestyle,
        
        @Schema(description = "Number of partner benefits", example = "7")
        int partners,
        
        @Schema(description = "Number of digital benefits", example = "2")
        int digital
    ) {}
    
    /**
     * Card tier enumeration.
     */
    public enum CardTier {
        STANDARD(0),
        GOLD(1),
        PLATINUM(2),
        BLACK(3);
        
        private final int level;
        
        CardTier(int level) {
            this.level = level;
        }
        
        public int getLevel() {
            return level;
        }
        
        public boolean canAccess(CardTier required) {
            return this.level >= required.level;
        }
        
        public static CardTier fromString(String value) {
            if (value == null || value.isBlank()) return STANDARD;
            try {
                return valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return STANDARD;
            }
        }
    }
    
    private static final String DISCLAIMER_EN = "Benefits are subject to terms and conditions. Some services may require additional registration.";
    private static final String DISCLAIMER_DE = "Leistungen unterliegen den allgemeinen Geschäftsbedingungen. Einige Dienste erfordern möglicherweise eine zusätzliche Registrierung.";
    
    /**
     * Factory method to create BenefitsDto from data.
     */
    public static BenefitsDto of(
            String cardTier,
            List<BenefitItem> benefits,
            String upgradeSuggestion,
            String language
    ) {
        Map<String, List<BenefitItem>> byCategory = benefits.stream()
            .collect(Collectors.groupingBy(BenefitItem::category));
        
        CategorySummary summary = new CategorySummary(
            byCategory.getOrDefault("INSURANCE", List.of()).size(),
            byCategory.getOrDefault("TRAVEL", List.of()).size(),
            byCategory.getOrDefault("LIFESTYLE", List.of()).size(),
            byCategory.getOrDefault("PARTNERS", List.of()).size(),
            byCategory.getOrDefault("DIGITAL", List.of()).size()
        );
        
        String voiceResponse = formatVoiceResponse(cardTier, summary, language);
        String disclaimer = "de".equalsIgnoreCase(language) ? DISCLAIMER_DE : DISCLAIMER_EN;
        
        return new BenefitsDto(
            cardTier,
            benefits.size(),
            byCategory,
            benefits,
            summary,
            upgradeSuggestion,
            voiceResponse,
            "EUR",
            disclaimer
        );
    }
    
    private static String formatVoiceResponse(String cardTier, CategorySummary summary, String language) {
        int total = summary.insurance + summary.travel + summary.lifestyle + summary.partners + summary.digital;
        
        if ("de".equalsIgnoreCase(language)) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("Mit Ihrer %s-Karte haben Sie Zugang zu %d Vorteilen. ", cardTier, total));
            if (summary.insurance > 0) sb.append(String.format("Versicherungen: %d. ", summary.insurance));
            if (summary.travel > 0) sb.append(String.format("Reise: %d. ", summary.travel));
            if (summary.lifestyle > 0) sb.append(String.format("Lifestyle: %d. ", summary.lifestyle));
            if (summary.partners > 0) sb.append(String.format("Partner: %d. ", summary.partners));
            sb.append("Möchten Sie Details zu einer bestimmten Kategorie?");
            return sb.toString();
        } else {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("With your %s card, you have access to %d benefits. ", cardTier, total));
            if (summary.insurance > 0) sb.append(String.format("Insurance: %d. ", summary.insurance));
            if (summary.travel > 0) sb.append(String.format("Travel: %d. ", summary.travel));
            if (summary.lifestyle > 0) sb.append(String.format("Lifestyle: %d. ", summary.lifestyle));
            if (summary.partners > 0) sb.append(String.format("Partners: %d. ", summary.partners));
            sb.append("Would you like details on a specific category?");
            return sb.toString();
        }
    }
}
