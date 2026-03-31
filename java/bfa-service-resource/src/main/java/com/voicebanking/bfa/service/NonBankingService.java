package com.voicebanking.bfa.service;

import com.voicebanking.bfa.dto.*;
import com.voicebanking.bfa.dto.BenefitsDto.BenefitItem;
import com.voicebanking.bfa.dto.BenefitsDto.CardTier;
import com.voicebanking.bfa.dto.InsuranceInfoDto.InsuranceCoverage;
import com.voicebanking.bfa.dto.InsuranceInfoDto.ClaimContact;
import com.voicebanking.bfa.dto.TravelBenefitsDto.TravelBenefit;
import com.voicebanking.bfa.dto.TravelBenefitsDto.MilesInfo;
import com.voicebanking.bfa.dto.PartnerOffersDto.PartnerOffer;
import com.voicebanking.bfa.dto.ServiceContactDto.ServiceContact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for non-banking operations.
 * 
 * <p>Provides information about bank-offered third-party services including
 * insurance partnerships, travel benefits, lifestyle perks, and partner offers.</p>
 * 
 * <p>Migrated from NonBankingServicesAgent.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-03
 */
@Service
public class NonBankingService {
    
    private static final Logger log = LoggerFactory.getLogger(NonBankingService.class);
    
    // Mock data for benefits catalog
    private final Map<String, List<BenefitItem>> benefitsCatalog;
    private final Map<String, InsuranceCoverage> insuranceCatalog;
    private final Map<String, TravelBenefit> travelBenefitsCatalog;
    private final List<PartnerOffer> partnerOffersCatalog;
    private final Map<String, ServiceContact> contactsCatalog;
    
    public NonBankingService() {
        this.benefitsCatalog = initBenefitsCatalog();
        this.insuranceCatalog = initInsuranceCatalog();
        this.travelBenefitsCatalog = initTravelBenefitsCatalog();
        this.partnerOffersCatalog = initPartnerOffersCatalog();
        this.contactsCatalog = initContactsCatalog();
    }
    
    /**
     * Get all benefits for a customer based on their card tier.
     */
    public BenefitsDto getMyBenefits(String cardTierStr, String category, String language) {
        log.debug("Getting benefits for tier={} category={} lang={}", cardTierStr, category, language);
        
        CardTier cardTier = CardTier.fromString(cardTierStr);
        
        List<BenefitItem> allBenefits = new ArrayList<>();
        
        // Collect benefits from all categories
        for (Map.Entry<String, List<BenefitItem>> entry : benefitsCatalog.entrySet()) {
            for (BenefitItem benefit : entry.getValue()) {
                CardTier requiredTier = CardTier.fromString(benefit.minimumTier());
                boolean eligible = cardTier.canAccess(requiredTier);
                
                allBenefits.add(new BenefitItem(
                    benefit.id(),
                    benefit.name(),
                    benefit.description(),
                    benefit.category(),
                    benefit.minimumTier(),
                    eligible,
                    benefit.value(),
                    benefit.partnerName()
                ));
            }
        }
        
        // Filter by category if specified
        if (category != null && !category.isBlank()) {
            String upperCategory = category.toUpperCase();
            allBenefits = allBenefits.stream()
                .filter(b -> b.category().equalsIgnoreCase(upperCategory))
                .collect(Collectors.toList());
        }
        
        // Generate upgrade suggestion
        String upgradeSuggestion = getUpgradeSuggestion(cardTier, language);
        
        return BenefitsDto.of(cardTier.name(), allBenefits, upgradeSuggestion, language);
    }
    
    /**
     * Get insurance information.
     */
    public InsuranceInfoDto getInsuranceInfo(String insuranceType, String cardTierStr, String language) {
        log.debug("Getting insurance info for type={} tier={} lang={}", insuranceType, cardTierStr, language);
        
        CardTier cardTier = CardTier.fromString(cardTierStr);
        
        if (insuranceType != null && !insuranceType.isBlank()) {
            // Return specific insurance
            InsuranceCoverage insurance = insuranceCatalog.get(insuranceType.toUpperCase());
            if (insurance == null) {
                // Return empty result with error message in voice response
                return InsuranceInfoDto.of(List.of(), cardTierStr, language);
            }
            
            // Check eligibility
            CardTier requiredTier = CardTier.fromString(insurance.minimumTier());
            boolean eligible = cardTier.canAccess(requiredTier);
            
            InsuranceCoverage withEligibility = eligible 
                ? insurance 
                : InsuranceCoverage.ofIneligible(
                    insurance.type(), insurance.name(), insurance.description(),
                    insurance.coverageLimit(), insurance.includes(), insurance.excludes(),
                    insurance.minimumTier(), 
                    getEligibilityMessage(requiredTier, language),
                    insurance.partnerName()
                  );
            
            return InsuranceInfoDto.ofSingle(withEligibility, cardTierStr, language);
        }
        
        // Return all insurances with eligibility
        List<InsuranceCoverage> insurances = insuranceCatalog.values().stream()
            .map(ins -> {
                CardTier requiredTier = CardTier.fromString(ins.minimumTier());
                boolean eligible = cardTier.canAccess(requiredTier);
                
                if (eligible) {
                    return ins;
                } else {
                    return InsuranceCoverage.ofIneligible(
                        ins.type(), ins.name(), ins.description(),
                        ins.coverageLimit(), ins.includes(), ins.excludes(),
                        ins.minimumTier(),
                        getEligibilityMessage(requiredTier, language),
                        ins.partnerName()
                    );
                }
            })
            .toList();
        
        return InsuranceInfoDto.of(insurances, cardTierStr, language);
    }
    
    /**
     * Get travel benefits.
     */
    public TravelBenefitsDto getTravelBenefits(
            String benefitType, String cardTierStr, String customerId, String language
    ) {
        log.debug("Getting travel benefits type={} tier={} customerId={} lang={}", 
            benefitType, cardTierStr, customerId, language);
        
        CardTier cardTier = CardTier.fromString(cardTierStr);
        
        // Get miles info if customer ID provided
        MilesInfo milesInfo = customerId != null && !customerId.isBlank()
            ? getMilesInfo(customerId)
            : MilesInfo.notEnrolled();
        
        if (benefitType != null && !benefitType.isBlank()) {
            // Return specific benefit
            TravelBenefit benefit = travelBenefitsCatalog.get(benefitType.toUpperCase());
            if (benefit == null) {
                return TravelBenefitsDto.of(List.of(), cardTierStr, milesInfo, language);
            }
            
            CardTier requiredTier = CardTier.fromString(benefit.minimumTier());
            boolean eligible = cardTier.canAccess(requiredTier);
            
            TravelBenefit withEligibility = eligible
                ? benefit
                : TravelBenefit.ofIneligible(
                    benefit.type(), benefit.name(), benefit.description(),
                    benefit.minimumTier(), getEligibilityMessage(requiredTier, language)
                  );
            
            return TravelBenefitsDto.of(List.of(withEligibility), cardTierStr, milesInfo, language);
        }
        
        // Return all travel benefits with eligibility
        List<TravelBenefit> benefits = travelBenefitsCatalog.values().stream()
            .map(benefit -> {
                CardTier requiredTier = CardTier.fromString(benefit.minimumTier());
                boolean eligible = cardTier.canAccess(requiredTier);
                
                if (eligible) {
                    return benefit;
                } else {
                    return TravelBenefit.ofIneligible(
                        benefit.type(), benefit.name(), benefit.description(),
                        benefit.minimumTier(), getEligibilityMessage(requiredTier, language)
                    );
                }
            })
            .toList();
        
        return TravelBenefitsDto.of(benefits, cardTierStr, milesInfo, language);
    }
    
    /**
     * Get partner offers.
     */
    public PartnerOffersDto getPartnerOffers(
            String offerType, String cardTierStr, boolean validOnly, String language
    ) {
        log.debug("Getting partner offers type={} tier={} validOnly={} lang={}", 
            offerType, cardTierStr, validOnly, language);
        
        CardTier cardTier = CardTier.fromString(cardTierStr);
        
        List<PartnerOffer> offers = partnerOffersCatalog.stream()
            .filter(offer -> {
                // Filter by offer type
                if (offerType != null && !offerType.isBlank()) {
                    if (!offer.offerType().equalsIgnoreCase(offerType)) {
                        return false;
                    }
                }
                
                // Filter by validity
                if (validOnly && !offer.currentlyValid()) {
                    return false;
                }
                
                return true;
            })
            .map(offer -> {
                // Check eligibility
                CardTier requiredTier = CardTier.fromString(offer.minimumTier());
                boolean eligible = cardTier.canAccess(requiredTier);
                
                // Return with updated eligibility
                return new PartnerOffer(
                    offer.offerId(), offer.partnerName(), offer.partnerLogoUrl(),
                    offer.title(), offer.description(), offer.offerType(),
                    offer.discountPercentage(), offer.cashbackPercentage(), offer.bonusPoints(),
                    offer.minimumPurchase(), offer.maximumDiscount(),
                    offer.validFrom(), offer.validUntil(), offer.currentlyValid(), offer.expiringSoon(),
                    offer.promoCode(), offer.redemptionInstructions(), offer.partnerUrl(),
                    offer.category(), offer.minimumTier(), eligible
                );
            })
            .toList();
        
        return PartnerOffersDto.of(offers, cardTierStr, language);
    }
    
    /**
     * Get service contact information.
     */
    public ServiceContactDto getServiceContact(String contactType, String serviceId, String language) {
        log.debug("Getting service contact type={} serviceId={} lang={}", contactType, serviceId, language);
        
        if (contactType != null && contactType.equalsIgnoreCase("EMERGENCY")) {
            return ServiceContactDto.emergencyOnly(language);
        }
        
        if (contactType != null && !contactType.isBlank()) {
            ServiceContact contact = contactsCatalog.get(contactType.toUpperCase());
            if (contact != null) {
                return ServiceContactDto.ofSingle(contact, language);
            }
        }
        
        if (serviceId != null && !serviceId.isBlank()) {
            // Find contact for specific service
            ServiceContact contact = contactsCatalog.values().stream()
                .filter(c -> c.serviceName().equalsIgnoreCase(serviceId))
                .findFirst()
                .orElse(null);
            
            if (contact != null) {
                return ServiceContactDto.ofSingle(contact, language);
            }
        }
        
        // Return all contacts
        List<ServiceContact> contacts = new ArrayList<>(contactsCatalog.values());
        return ServiceContactDto.of(contacts, language);
    }
    
    // ========================
    // Private Helper Methods
    // ========================
    
    private String getUpgradeSuggestion(CardTier currentTier, String language) {
        if (currentTier == CardTier.BLACK) {
            return null; // Already highest tier
        }
        
        CardTier nextTier = switch (currentTier) {
            case STANDARD -> CardTier.GOLD;
            case GOLD -> CardTier.PLATINUM;
            case PLATINUM -> CardTier.BLACK;
            default -> null;
        };
        
        if (nextTier == null) return null;
        
        int additionalBenefits = (nextTier.getLevel() - currentTier.getLevel()) * 5; // Rough estimate
        
        if ("de".equalsIgnoreCase(language)) {
            return String.format(
                "Mit einem Upgrade auf %s erhalten Sie %d weitere Vorteile, darunter erweiterte Versicherungen und exklusive Partner-Angebote.",
                nextTier.name(), additionalBenefits
            );
        } else {
            return String.format(
                "Upgrade to %s to unlock %d additional benefits including enhanced insurance and exclusive partner offers.",
                nextTier.name(), additionalBenefits
            );
        }
    }
    
    private String getEligibilityMessage(CardTier requiredTier, String language) {
        if ("de".equalsIgnoreCase(language)) {
            return String.format(
                "Dieser Vorteil erfordert mindestens eine %s-Karte. Möchten Sie Informationen zum Upgrade?",
                requiredTier.name()
            );
        } else {
            return String.format(
                "This benefit requires at least a %s card. Would you like information about upgrading?",
                requiredTier.name()
            );
        }
    }
    
    private MilesInfo getMilesInfo(String customerId) {
        // Mock Miles & More data
        return MilesInfo.of(
            "MM" + customerId.hashCode(),
            45230 + (int)(Math.random() * 10000),
            (int)(Math.random() * 5000),
            Math.random() > 0.7 ? "SENATOR" : (Math.random() > 0.4 ? "FREQUENT_TRAVELLER" : "MEMBER"),
            35000 + (int)(Math.random() * 15000)
        );
    }
    
    // ========================
    // Catalog Initialization
    // ========================
    
    private Map<String, List<BenefitItem>> initBenefitsCatalog() {
        Map<String, List<BenefitItem>> catalog = new HashMap<>();
        
        catalog.put("INSURANCE", List.of(
            new BenefitItem("INS-001", "Travel Medical Insurance", "Coverage up to €1M for medical emergencies abroad", "INSURANCE", "GOLD", true, "1000000", "AXA"),
            new BenefitItem("INS-002", "Trip Cancellation Insurance", "Coverage for trip cancellation up to €10K", "INSURANCE", "GOLD", true, "10000", "AXA"),
            new BenefitItem("INS-003", "Purchase Protection", "90-day protection for purchases up to €5K", "INSURANCE", "STANDARD", true, "5000", "AXA"),
            new BenefitItem("INS-004", "Rental Car CDW", "Collision damage waiver for rental cars", "INSURANCE", "PLATINUM", true, "50000", "AXA")
        ));
        
        catalog.put("TRAVEL", List.of(
            new BenefitItem("TRV-001", "Airport Lounge Access", "Priority Pass lounge access worldwide", "TRAVEL", "PLATINUM", true, "4 visits/year", "Priority Pass"),
            new BenefitItem("TRV-002", "Miles & More", "Earn and redeem miles with Lufthansa", "TRAVEL", "STANDARD", true, null, "Lufthansa"),
            new BenefitItem("TRV-003", "Travel Concierge", "24/7 travel assistance and booking", "TRAVEL", "PLATINUM", true, null, "Acme Bank"),
            new BenefitItem("TRV-004", "Fast Track Security", "Priority security at select airports", "TRAVEL", "BLACK", true, null, "Priority Pass"),
            new BenefitItem("TRV-005", "Hotel Benefits", "Room upgrades and late checkout", "TRAVEL", "GOLD", true, null, "Various"),
            new BenefitItem("TRV-006", "Car Rental Discounts", "Up to 25% off at Sixt and Hertz", "TRAVEL", "STANDARD", true, "25%", "Sixt, Hertz")
        ));
        
        catalog.put("LIFESTYLE", List.of(
            new BenefitItem("LIF-001", "Premium Events", "Access to exclusive events and concerts", "LIFESTYLE", "PLATINUM", true, null, "Acme Bank"),
            new BenefitItem("LIF-002", "Personal Shopper", "Personal shopping assistance", "LIFESTYLE", "BLACK", true, null, "Acme Bank"),
            new BenefitItem("LIF-003", "Golf Access", "Access to premium golf courses", "LIFESTYLE", "PLATINUM", true, null, "Various")
        ));
        
        catalog.put("PARTNERS", List.of(
            new BenefitItem("PAR-001", "Zalando Discount", "15% off fashion purchases", "PARTNERS", "STANDARD", true, "15%", "Zalando"),
            new BenefitItem("PAR-002", "MediaMarkt Discount", "10% off electronics", "PARTNERS", "STANDARD", true, "10%", "MediaMarkt"),
            new BenefitItem("PAR-003", "Booking.com Cashback", "5% cashback on hotel bookings", "PARTNERS", "GOLD", true, "5%", "Booking.com"),
            new BenefitItem("PAR-004", "Lufthansa Shop Discount", "20% off Lufthansa Shop", "PARTNERS", "GOLD", true, "20%", "Lufthansa")
        ));
        
        catalog.put("DIGITAL", List.of(
            new BenefitItem("DIG-001", "Identity Protection", "Identity theft monitoring and alerts", "DIGITAL", "GOLD", true, null, "Acme Bank"),
            new BenefitItem("DIG-002", "Cyber Security", "Device security software", "DIGITAL", "PLATINUM", true, null, "Norton")
        ));
        
        return catalog;
    }
    
    private Map<String, InsuranceCoverage> initInsuranceCatalog() {
        Map<String, InsuranceCoverage> catalog = new HashMap<>();
        
        ClaimContact axaContact = new ClaimContact(
            "+49 800 100-2500",
            "claims@axa.de",
            "https://claims.axa.de",
            "24/7",
            List.of("Claim form", "Medical receipts", "Police report (if applicable)")
        );
        
        catalog.put("TRAVEL_MEDICAL", InsuranceCoverage.ofEligible(
            "TRAVEL_MEDICAL",
            "Travel Medical Insurance",
            "Emergency medical treatment, hospital stays, and medical evacuation abroad.",
            new BigDecimal("1000000"),
            List.of("Hospital stays", "Doctor visits", "Emergency evacuation", "Repatriation"),
            List.of("Pre-existing conditions", "Elective procedures", "Dental (unless emergency)"),
            "GOLD",
            "AXA Insurance",
            axaContact
        ));
        
        catalog.put("TRIP_CANCELLATION", InsuranceCoverage.ofEligible(
            "TRIP_CANCELLATION",
            "Trip Cancellation Insurance",
            "Reimbursement for non-refundable trip costs if you must cancel.",
            new BigDecimal("10000"),
            List.of("Flight cancellation", "Hotel cancellation", "Tour cancellation"),
            List.of("Change of mind", "Work conflicts", "Pre-existing conditions"),
            "GOLD",
            "AXA Insurance",
            axaContact
        ));
        
        catalog.put("PURCHASE_PROTECTION", InsuranceCoverage.ofEligible(
            "PURCHASE_PROTECTION",
            "Purchase Protection",
            "Protection against theft and accidental damage for 90 days after purchase.",
            new BigDecimal("5000"),
            List.of("Theft", "Accidental damage", "Extended warranty"),
            List.of("Consumables", "Cash", "Motor vehicles"),
            "STANDARD",
            "AXA Insurance",
            axaContact
        ));
        
        catalog.put("RENTAL_CAR_CDW", InsuranceCoverage.ofEligible(
            "RENTAL_CAR_CDW",
            "Rental Car CDW",
            "Collision damage waiver covering rental car damage or theft.",
            new BigDecimal("50000"),
            List.of("Collision damage", "Theft", "Vandalism"),
            List.of("Personal injury", "Third-party liability", "Off-road use"),
            "PLATINUM",
            "AXA Insurance",
            axaContact
        ));
        
        return catalog;
    }
    
    private Map<String, TravelBenefit> initTravelBenefitsCatalog() {
        Map<String, TravelBenefit> catalog = new HashMap<>();
        
        catalog.put("AIRPORT_LOUNGE", TravelBenefit.ofEligible(
            "AIRPORT_LOUNGE",
            "Priority Pass Lounge Access",
            "Access to 1,300+ airport lounges worldwide",
            "PLATINUM",
            4, 1, "Priority Pass",
            "Present your Priority Pass digital card at the lounge entrance",
            "Priority Pass App"
        ));
        
        catalog.put("MILES_AND_MORE", TravelBenefit.ofEligible(
            "MILES_AND_MORE",
            "Miles & More Program",
            "Earn 1 mile per €2 spent, plus bonus miles on flights",
            "STANDARD",
            null, null, "Lufthansa",
            "Link your Miles & More account in the Acme Bank app",
            "Miles & More App"
        ));
        
        catalog.put("TRAVEL_CONCIERGE", TravelBenefit.ofEligible(
            "TRAVEL_CONCIERGE",
            "Travel Concierge",
            "24/7 travel planning, booking, and assistance",
            "PLATINUM",
            null, null, "Acme Bank",
            "Call the concierge hotline or use the in-app chat",
            null
        ));
        
        catalog.put("FAST_TRACK", TravelBenefit.ofEligible(
            "FAST_TRACK",
            "Fast Track Security",
            "Priority security lanes at major airports",
            "BLACK",
            null, null, "Priority Pass",
            "Show your Black card at the Fast Track entrance",
            null
        ));
        
        catalog.put("HOTEL_BENEFITS", TravelBenefit.ofEligible(
            "HOTEL_BENEFITS",
            "Hotel Benefits",
            "Room upgrades, late checkout, and welcome amenities",
            "GOLD",
            null, null, "Various Hotels",
            "Book through the Acme Bank travel portal to receive benefits",
            null
        ));
        
        catalog.put("CAR_RENTAL", TravelBenefit.ofEligible(
            "CAR_RENTAL",
            "Car Rental Discounts",
            "Up to 25% off at Sixt, Hertz, and Europcar",
            "STANDARD",
            null, null, "Multiple Partners",
            "Use your Acme Bank promo code when booking",
            null
        ));
        
        return catalog;
    }
    
    private List<PartnerOffer> initPartnerOffersCatalog() {
        LocalDate today = LocalDate.now();
        
        return List.of(
            PartnerOffer.discount(
                "OFFER-ZAL-001", "Zalando", "15% off Fashion",
                "Get 15% off your fashion purchase at Zalando",
                new BigDecimal("15"), new BigDecimal("50"), new BigDecimal("100"),
                today.minusDays(30), today.plusDays(60), "ACME15",
                "FASHION", "STANDARD", true
            ),
            PartnerOffer.discount(
                "OFFER-MM-001", "MediaMarkt", "10% off Electronics",
                "Get 10% off electronics at MediaMarkt",
                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("200"),
                today.minusDays(15), today.plusDays(45), "ACME10",
                "ELECTRONICS", "STANDARD", true
            ),
            PartnerOffer.cashback(
                "OFFER-BOOK-001", "Booking.com", "5% Cashback",
                "Earn 5% cashback on all hotel bookings",
                new BigDecimal("5"),
                today.minusDays(60), today.plusDays(120),
                "TRAVEL", "GOLD", true
            ),
            PartnerOffer.discount(
                "OFFER-LH-001", "Lufthansa Shop", "20% off",
                "Get 20% off at Lufthansa WorldShop",
                new BigDecimal("20"), null, null,
                today.minusDays(10), today.plusDays(5), "LHACME20",
                "TRAVEL", "GOLD", true
            ),
            PartnerOffer.cashback(
                "OFFER-IKEA-001", "IKEA", "3% Cashback",
                "Earn 3% cashback on IKEA purchases",
                new BigDecimal("3"),
                today.minusDays(90), today.plusDays(90),
                "HOME", "STANDARD", true
            ),
            PartnerOffer.discount(
                "OFFER-SIXT-001", "Sixt", "25% off Car Rental",
                "Get 25% off car rentals at Sixt",
                new BigDecimal("25"), null, null,
                today.minusDays(30), today.plusDays(60), "SIXTACME",
                "TRAVEL", "STANDARD", true
            ),
            PartnerOffer.discount(
                "OFFER-CHRIST-001", "Christ Jewelers", "10% off",
                "Get 10% off jewelry at Christ",
                new BigDecimal("10"), new BigDecimal("200"), new BigDecimal("500"),
                today.minusDays(7), today.plusDays(3), "CHRISTACME",
                "LUXURY", "PLATINUM", true
            )
        );
    }
    
    private Map<String, ServiceContact> initContactsCatalog() {
        Map<String, ServiceContact> catalog = new HashMap<>();
        
        catalog.put("CARD_SERVICES", new ServiceContact(
            "CARD_SERVICES",
            "Card Services",
            "+49 69 910-10000",
            "+49 800 123-4567",
            "+49 69 910-10000",
            "cards@acmebank.example",
            "https://acmebank.example/cards",
            "Mon-Fri 8:00-20:00, Sat 9:00-14:00",
            List.of("German", "English"),
            "For card blocking, use the emergency hotline instead."
        ));
        
        catalog.put("CONCIERGE", new ServiceContact(
            "CONCIERGE",
            "Concierge Service",
            "+49 69 910-10100",
            "+49 800 123-4600",
            "+49 69 910-10100",
            "concierge@acmebank.example",
            "https://acmebank.example/concierge",
            "24/7",
            List.of("German", "English", "French"),
            "Available for Platinum and Black cardholders."
        ));
        
        catalog.put("INSURANCE_CLAIMS", new ServiceContact(
            "INSURANCE_CLAIMS",
            "Insurance Claims",
            "+49 800 100-2500",
            "+49 800 100-2500",
            "+49 69 910-12500",
            "claims@axa.de",
            "https://claims.axa.de",
            "24/7",
            List.of("German", "English"),
            "Provided by our partner AXA Insurance."
        ));
        
        catalog.put("TRAVEL_ASSISTANCE", new ServiceContact(
            "TRAVEL_ASSISTANCE",
            "Travel Assistance",
            "+49 69 910-10200",
            "+49 800 123-4700",
            "+49 69 910-10200",
            "travel@acmebank.example",
            "https://acmebank.example/travel",
            "24/7",
            List.of("German", "English", "French", "Spanish"),
            "For travel emergencies, use the emergency hotline."
        ));
        
        catalog.put("TECHNICAL_SUPPORT", new ServiceContact(
            "TECHNICAL_SUPPORT",
            "Technical Support",
            "+49 69 910-10300",
            "+49 800 123-4800",
            "+49 69 910-10300",
            "support@acmebank.example",
            "https://acmebank.example/support",
            "Mon-Fri 8:00-22:00, Sat-Sun 9:00-18:00",
            List.of("German", "English"),
            "For app and online banking issues."
        ));
        
        return catalog;
    }
}
