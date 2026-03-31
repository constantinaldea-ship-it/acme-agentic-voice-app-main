package com.voicebanking.bfa.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO for partner offers and discounts.
 * 
 * <p>Provides information about current partner offers, discounts,
 * cashback programs, and exclusive access available to cardholders.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-03
 */
@Schema(description = "Partner offers and discounts")
public record PartnerOffersDto(
    @Schema(description = "List of partner offers")
    List<PartnerOffer> offers,
    
    @Schema(description = "Number of offers", example = "7")
    int count,
    
    @Schema(description = "Number of offers expiring soon", example = "2")
    int expiringSoon,
    
    @Schema(description = "Customer's card tier", example = "GOLD")
    String cardTier,
    
    @Schema(description = "Voice-friendly response")
    String voiceResponse,
    
    @Schema(description = "Regulatory disclaimer")
    String disclaimer
) {
    
    /**
     * Individual partner offer.
     */
    @Schema(description = "Partner offer details")
    public record PartnerOffer(
        @Schema(description = "Unique offer ID", example = "OFFER-ZAL-2026-001")
        String offerId,
        
        @Schema(description = "Partner name", example = "Zalando")
        String partnerName,
        
        @Schema(description = "Partner logo URL")
        String partnerLogoUrl,
        
        @Schema(description = "Offer title", example = "15% off Fashion")
        String title,
        
        @Schema(description = "Offer description")
        String description,
        
        @Schema(description = "Offer type: DISCOUNT, CASHBACK, BONUS_POINTS, EXCLUSIVE_ACCESS")
        String offerType,
        
        @Schema(description = "Discount percentage if applicable", example = "15.0")
        BigDecimal discountPercentage,
        
        @Schema(description = "Cashback percentage if applicable", example = "5.0")
        BigDecimal cashbackPercentage,
        
        @Schema(description = "Bonus points if applicable", example = "500")
        Integer bonusPoints,
        
        @Schema(description = "Minimum purchase amount if applicable", example = "50.00")
        BigDecimal minimumPurchase,
        
        @Schema(description = "Maximum discount amount if applicable", example = "100.00")
        BigDecimal maximumDiscount,
        
        @Schema(description = "Offer start date")
        LocalDate validFrom,
        
        @Schema(description = "Offer end date")
        LocalDate validUntil,
        
        @Schema(description = "Whether offer is currently valid")
        boolean currentlyValid,
        
        @Schema(description = "Whether offer expires within 7 days")
        boolean expiringSoon,
        
        @Schema(description = "Promo code if applicable", example = "ACME15")
        String promoCode,
        
        @Schema(description = "How to redeem the offer")
        String redemptionInstructions,
        
        @Schema(description = "Partner website URL")
        String partnerUrl,
        
        @Schema(description = "Offer category", example = "FASHION")
        String category,
        
        @Schema(description = "Minimum card tier required", example = "GOLD")
        String minimumTier,
        
        @Schema(description = "Whether customer is eligible")
        boolean eligible
    ) {
        /**
         * Factory method for discount offer.
         */
        public static PartnerOffer discount(
                String offerId, String partnerName, String title, String description,
                BigDecimal discountPercentage, BigDecimal minimumPurchase, BigDecimal maximumDiscount,
                LocalDate validFrom, LocalDate validUntil, String promoCode,
                String category, String minimumTier, boolean eligible
        ) {
            boolean currentlyValid = !LocalDate.now().isBefore(validFrom) && !LocalDate.now().isAfter(validUntil);
            boolean expiring = validUntil.isBefore(LocalDate.now().plusDays(7));
            
            return new PartnerOffer(
                offerId, partnerName, null, title, description,
                "DISCOUNT", discountPercentage, null, null,
                minimumPurchase, maximumDiscount,
                validFrom, validUntil, currentlyValid, expiring,
                promoCode, "Use promo code at checkout",
                "https://" + partnerName.toLowerCase().replace(" ", "") + ".com",
                category, minimumTier, eligible
            );
        }
        
        /**
         * Factory method for cashback offer.
         */
        public static PartnerOffer cashback(
                String offerId, String partnerName, String title, String description,
                BigDecimal cashbackPercentage, LocalDate validFrom, LocalDate validUntil,
                String category, String minimumTier, boolean eligible
        ) {
            boolean currentlyValid = !LocalDate.now().isBefore(validFrom) && !LocalDate.now().isAfter(validUntil);
            boolean expiring = validUntil.isBefore(LocalDate.now().plusDays(7));
            
            return new PartnerOffer(
                offerId, partnerName, null, title, description,
                "CASHBACK", null, cashbackPercentage, null,
                null, null,
                validFrom, validUntil, currentlyValid, expiring,
                null, "Cashback is credited automatically when paying with your Acme Bank card",
                "https://" + partnerName.toLowerCase().replace(" ", "") + ".com",
                category, minimumTier, eligible
            );
        }
    }
    
    /**
     * Offer type enumeration.
     */
    public enum OfferType {
        DISCOUNT("Discount", "Rabatt"),
        CASHBACK("Cashback", "Cashback"),
        BONUS_POINTS("Bonus Points", "Bonuspunkte"),
        EXCLUSIVE_ACCESS("Exclusive Access", "Exklusiver Zugang");
        
        private final String nameEn;
        private final String nameDe;
        
        OfferType(String nameEn, String nameDe) {
            this.nameEn = nameEn;
            this.nameDe = nameDe;
        }
        
        public String getName(String language) {
            return "de".equalsIgnoreCase(language) ? nameDe : nameEn;
        }
    }
    
    private static final String DISCLAIMER_EN = "Offers are provided by our partners and subject to their terms and conditions. Acme Bank is not responsible for partner products or services.";
    private static final String DISCLAIMER_DE = "Angebote werden von unseren Partnern bereitgestellt und unterliegen deren Geschäftsbedingungen. Acme Bank ist nicht verantwortlich für Partnerprodukte oder -dienstleistungen.";
    
    /**
     * Factory method to create PartnerOffersDto.
     */
    public static PartnerOffersDto of(
            List<PartnerOffer> offers,
            String cardTier,
            String language
    ) {
        int expiring = (int) offers.stream().filter(PartnerOffer::expiringSoon).count();
        String voiceResponse = formatVoiceResponse(offers, expiring, language);
        String disclaimer = "de".equalsIgnoreCase(language) ? DISCLAIMER_DE : DISCLAIMER_EN;
        
        return new PartnerOffersDto(
            offers,
            offers.size(),
            expiring,
            cardTier,
            voiceResponse,
            disclaimer
        );
    }
    
    private static String formatVoiceResponse(List<PartnerOffer> offers, int expiring, String language) {
        if (offers.isEmpty()) {
            return "de".equalsIgnoreCase(language) 
                ? "Derzeit sind keine Partnerangebote verfügbar."
                : "No partner offers are currently available.";
        }
        
        StringBuilder sb = new StringBuilder();
        
        if ("de".equalsIgnoreCase(language)) {
            sb.append(String.format("Sie haben Zugang zu %d Partnerangeboten. ", offers.size()));
            if (expiring > 0) {
                sb.append(String.format("%d Angebot%s läuft bald ab. ", expiring, expiring > 1 ? "e" : ""));
            }
            
            // Highlight top 3 offers
            int count = 0;
            for (PartnerOffer offer : offers) {
                if (count++ >= 3) break;
                if (offer.offerType().equals("DISCOUNT")) {
                    sb.append(String.format("%s: %s%% Rabatt. ", 
                        offer.partnerName(), offer.discountPercentage().intValue()));
                } else if (offer.offerType().equals("CASHBACK")) {
                    sb.append(String.format("%s: %s%% Cashback. ", 
                        offer.partnerName(), offer.cashbackPercentage().intValue()));
                }
            }
            
            sb.append("Möchten Sie weitere Angebote hören?");
        } else {
            sb.append(String.format("You have access to %d partner offers. ", offers.size()));
            if (expiring > 0) {
                sb.append(String.format("%d offer%s expiring soon. ", expiring, expiring > 1 ? "s" : ""));
            }
            
            // Highlight top 3 offers
            int count = 0;
            for (PartnerOffer offer : offers) {
                if (count++ >= 3) break;
                if (offer.offerType().equals("DISCOUNT")) {
                    sb.append(String.format("%s: %s%% discount. ", 
                        offer.partnerName(), offer.discountPercentage().intValue()));
                } else if (offer.offerType().equals("CASHBACK")) {
                    sb.append(String.format("%s: %s%% cashback. ", 
                        offer.partnerName(), offer.cashbackPercentage().intValue()));
                }
            }
            
            sb.append("Would you like to hear more offers?");
        }
        
        return sb.toString();
    }
}
