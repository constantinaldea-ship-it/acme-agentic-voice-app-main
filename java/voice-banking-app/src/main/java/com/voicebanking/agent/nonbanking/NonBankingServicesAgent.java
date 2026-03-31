package com.voicebanking.agent.nonbanking;

import com.voicebanking.agent.Agent;
import com.voicebanking.agent.nonbanking.catalog.ServicesCatalog;
import com.voicebanking.agent.nonbanking.domain.*;
import com.voicebanking.agent.nonbanking.integration.InsurancePartnerStub;
import com.voicebanking.agent.nonbanking.integration.MilesAndMoreStub;
import com.voicebanking.agent.nonbanking.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * NonBankingServicesAgent
 * 
 * Provides information about bank-offered third-party services and ancillary products.
 * This includes insurance partnerships, travel services, lifestyle benefits, and 
 * exclusive offers for Acme Bank customers.
 * 
 * <h2>Tools Provided</h2>
 * <ul>
 *   <li><b>getMyBenefits</b> - List all benefits for customer's card tier</li>
 *   <li><b>getInsuranceInfo</b> - Get insurance coverage details</li>
 *   <li><b>getTravelBenefits</b> - Get travel-related benefits</li>
 *   <li><b>getPartnerOffers</b> - Get current partner offers and discounts</li>
 *   <li><b>getServiceContact</b> - Get contact information for services</li>
 * </ul>
 * 
 * <h2>Category</h2>
 * Category 2: Voice-Enabled Context-Aware Banking (Read)
 * 
 * <h2>Key Responsibilities</h2>
 * <ul>
 *   <li>Provide information on insurance partnerships</li>
 *   <li>Describe travel services (Miles & More, lounges)</li>
 *   <li>Explain lifestyle benefits (concierge, events)</li>
 *   <li>List exclusive partner offers</li>
 *   <li>Clarify what's NOT available (out-of-scope)</li>
 * </ul>
 * 
 * @author Augment Agent
 * @since 2026-01-25
 * @see Agent
 */
@Component
public class NonBankingServicesAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(NonBankingServicesAgent.class);

    private static final String AGENT_ID = "non-banking-services";
    private static final List<String> TOOL_IDS = List.of(
            "getMyBenefits",
            "getInsuranceInfo",
            "getTravelBenefits",
            "getPartnerOffers",
            "getServiceContact"
    );

    private final ServicesCatalog catalog;
    private final BenefitsService benefitsService;
    private final EligibilityCheckerService eligibilityChecker;
    private final ServiceContactService contactService;
    private final MilesAndMoreStub milesAndMoreStub;
    private final InsurancePartnerStub insurancePartnerStub;

    public NonBankingServicesAgent(
            ServicesCatalog catalog,
            BenefitsService benefitsService,
            EligibilityCheckerService eligibilityChecker,
            ServiceContactService contactService,
            MilesAndMoreStub milesAndMoreStub,
            InsurancePartnerStub insurancePartnerStub) {
        this.catalog = catalog;
        this.benefitsService = benefitsService;
        this.eligibilityChecker = eligibilityChecker;
        this.contactService = contactService;
        this.milesAndMoreStub = milesAndMoreStub;
        this.insurancePartnerStub = insurancePartnerStub;
        log.info("NonBankingServicesAgent initialized with {} tools", TOOL_IDS.size());
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    @Override
    public String getDescription() {
        return "Provides information about non-banking services including insurance, travel benefits, " +
               "lifestyle perks, and partner offers available to Acme Bank card holders";
    }

    @Override
    public List<String> getToolIds() {
        return TOOL_IDS;
    }

    @Override
    public Map<String, Object> executeTool(String toolId, Map<String, Object> input) {
        log.debug("Executing tool: {} with input: {}", toolId, input);

        try {
            return switch (toolId) {
                case "getMyBenefits" -> getMyBenefits(input);
                case "getInsuranceInfo" -> getInsuranceInfo(input);
                case "getTravelBenefits" -> getTravelBenefits(input);
                case "getPartnerOffers" -> getPartnerOffers(input);
                case "getServiceContact" -> getServiceContact(input);
                default -> Map.of("error", "Unknown tool: " + toolId, "success", false);
            };
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolId, e.getMessage(), e);
            return Map.of("error", e.getMessage(), "success", false);
        }
    }

    /**
     * Get all benefits available to the customer based on their card tier.
     * 
     * Input:
     * - cardTier: "STANDARD", "GOLD", "PLATINUM", "BLACK" (optional, defaults to STANDARD)
     * - language: "en" or "de" (optional, defaults to "en")
     * - category: Filter by category (optional)
     * 
     * Output:
     * - benefits: List of benefits organized by category
     * - totalCount: Total number of benefits
     * - voiceResponse: Voice-friendly summary
     * - upgradeSuggestion: If applicable, what they'd get with upgrade
     */
    private Map<String, Object> getMyBenefits(Map<String, Object> input) {
        String cardTierStr = (String) input.getOrDefault("cardTier", "STANDARD");
        String lang = (String) input.getOrDefault("language", "en");
        String categoryStr = (String) input.get("category");
        
        CardTier cardTier = CardTier.fromString(cardTierStr);
        
        log.info("Getting benefits for card tier: {}", cardTier);
        
        BenefitsService.CustomerBenefits benefits = benefitsService.getBenefitsForCustomer(cardTier);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("cardTier", cardTier.name());
        result.put("totalCount", benefits.totalCount());
        
        // Filter by category if specified
        if (categoryStr != null) {
            ServiceCategory category = ServiceCategory.valueOf(categoryStr.toUpperCase());
            List<NonBankingService> filtered = benefits.byCategory().getOrDefault(category, List.of());
            result.put("benefits", filtered.stream().map(NonBankingService::toMap).collect(Collectors.toList()));
            result.put("voiceResponse", benefitsService.formatServicesForVoice(filtered, lang));
        } else {
            result.put("benefits", benefits.toMap());
            result.put("voiceResponse", benefitsService.getBenefitsSummaryForVoice(cardTier, lang));
        }
        
        // Include upgrade suggestion
        String upgrade = benefitsService.getUpgradeSuggestion(cardTier, lang);
        if (upgrade != null) {
            result.put("upgradeSuggestion", upgrade);
        }
        
        return result;
    }

    /**
     * Get detailed insurance coverage information.
     * 
     * Input:
     * - insuranceType: "TRAVEL_MEDICAL", "TRIP_CANCELLATION", "PURCHASE_PROTECTION", "RENTAL_CAR_CDW"
     * - cardTier: Customer's card tier (optional)
     * - language: "en" or "de" (optional, defaults to "en")
     * 
     * Output:
     * - insurance: Detailed insurance coverage info
     * - eligible: Whether customer is eligible
     * - claimContact: How to file a claim
     * - voiceResponse: Voice-friendly description
     */
    private Map<String, Object> getInsuranceInfo(Map<String, Object> input) {
        String insuranceTypeStr = (String) input.get("insuranceType");
        String cardTierStr = (String) input.getOrDefault("cardTier", "STANDARD");
        String lang = (String) input.getOrDefault("language", "en");
        
        if (insuranceTypeStr == null || insuranceTypeStr.isBlank()) {
            // Return all insurance types
            CardTier cardTier = CardTier.fromString(cardTierStr);
            List<InsuranceCoverage> insurances = benefitsService.getInsuranceBenefits(cardTier);
            
            return Map.of(
                "success", true,
                "insurances", insurances.stream().map(InsuranceCoverage::toMap).collect(Collectors.toList()),
                "count", insurances.size(),
                "voiceResponse", benefitsService.formatServicesForVoice(insurances, lang)
            );
        }
        
        InsuranceType insuranceType = InsuranceType.fromString(insuranceTypeStr);
        if (insuranceType == null) {
            return Map.of(
                "success", false,
                "error", "Unknown insurance type: " + insuranceTypeStr,
                "voiceResponse", "de".equalsIgnoreCase(lang) 
                    ? "Entschuldigung, diese Versicherungsart ist mir nicht bekannt."
                    : "Sorry, I don't recognize that insurance type."
            );
        }
        
        Optional<InsuranceCoverage> insuranceOpt = catalog.getInsuranceByType(insuranceType);
        if (insuranceOpt.isEmpty()) {
            return Map.of(
                "success", false,
                "error", "Insurance not found: " + insuranceType,
                "voiceResponse", "de".equalsIgnoreCase(lang)
                    ? "Diese Versicherung ist derzeit nicht verfügbar."
                    : "This insurance is not currently available."
            );
        }
        
        InsuranceCoverage insurance = insuranceOpt.get();
        CardTier cardTier = CardTier.fromString(cardTierStr);
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("insurance", insurance.toMap());
        
        // Check eligibility
        EligibilityCheckerService.EligibilityStatus eligibility = 
                eligibilityChecker.getEligibilityStatus(insurance, cardTier, lang);
        result.put("eligible", eligibility.eligible());
        
        if (!eligibility.eligible()) {
            result.put("eligibilityMessage", eligibility.formatForVoice(lang));
        }
        
        // Add claim contact info
        ServiceContactService.ClaimContact claimContact = 
                contactService.getClaimContact(insuranceType, lang);
        result.put("claimContact", claimContact.toMap());
        
        // Voice response
        if (eligibility.eligible()) {
            result.put("voiceResponse", insurance.formatForVoiceDetailed(lang));
        } else {
            result.put("voiceResponse", eligibility.formatForVoice(lang));
        }
        
        return result;
    }

    /**
     * Get travel-related benefits.
     * 
     * Input:
     * - benefitType: "AIRPORT_LOUNGE", "MILES_AND_MORE", "TRAVEL_CONCIERGE", etc. (optional)
     * - cardTier: Customer's card tier (optional)
     * - language: "en" or "de" (optional, defaults to "en")
     * 
     * Output:
     * - travelBenefit: Detailed benefit info
     * - eligible: Whether customer is eligible
     * - accessInstructions: How to use the benefit
     * - voiceResponse: Voice-friendly description
     */
    private Map<String, Object> getTravelBenefits(Map<String, Object> input) {
        String benefitTypeStr = (String) input.get("benefitType");
        String cardTierStr = (String) input.getOrDefault("cardTier", "STANDARD");
        String lang = (String) input.getOrDefault("language", "en");
        String customerId = (String) input.get("customerId");
        
        CardTier cardTier = CardTier.fromString(cardTierStr);
        
        if (benefitTypeStr == null || benefitTypeStr.isBlank()) {
            // Return all travel benefits for this tier
            List<TravelBenefit> benefits = benefitsService.getTravelBenefits(cardTier);
            
            return Map.of(
                "success", true,
                "travelBenefits", benefits.stream().map(TravelBenefit::toMap).collect(Collectors.toList()),
                "count", benefits.size(),
                "voiceResponse", benefitsService.formatServicesForVoice(benefits, lang)
            );
        }
        
        TravelBenefitType benefitType = TravelBenefitType.fromString(benefitTypeStr);
        if (benefitType == null) {
            return Map.of(
                "success", false,
                "error", "Unknown travel benefit type: " + benefitTypeStr,
                "voiceResponse", "de".equalsIgnoreCase(lang)
                    ? "Entschuldigung, diesen Reisevorteil kenne ich nicht."
                    : "Sorry, I don't recognize that travel benefit."
            );
        }
        
        Optional<TravelBenefit> benefitOpt = catalog.getTravelBenefitByType(benefitType);
        if (benefitOpt.isEmpty()) {
            return Map.of(
                "success", false,
                "error", "Travel benefit not found: " + benefitType,
                "voiceResponse", "de".equalsIgnoreCase(lang)
                    ? "Dieser Reisevorteil ist derzeit nicht verfügbar."
                    : "This travel benefit is not currently available."
            );
        }
        
        TravelBenefit benefit = benefitOpt.get();
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("travelBenefit", benefit.toMap());
        
        // Check eligibility
        EligibilityCheckerService.EligibilityStatus eligibility = 
                eligibilityChecker.getEligibilityStatus(benefit, cardTier, lang);
        result.put("eligible", eligibility.eligible());
        
        if (!eligibility.eligible()) {
            result.put("eligibilityMessage", eligibility.formatForVoice(lang));
        }
        
        // For Miles & More, try to get customer data
        if (benefitType == TravelBenefitType.MILES_AND_MORE && customerId != null) {
            milesAndMoreStub.getMilesSummary(customerId).ifPresent(summary -> {
                result.put("milesBalance", summary.awardMiles());
                result.put("milesStatus", summary.status());
                result.put("milesSummary", summary.formatForVoice(lang));
            });
        }
        
        // Voice response
        if (eligibility.eligible()) {
            result.put("voiceResponse", benefit.formatForVoiceDetailed(lang));
        } else {
            result.put("voiceResponse", eligibility.formatForVoice(lang));
        }
        
        return result;
    }

    /**
     * Get current partner offers.
     * 
     * Input:
     * - offerType: "DISCOUNT", "CASHBACK", "BONUS_POINTS", etc. (optional)
     * - cardTier: Customer's card tier (optional)
     * - language: "en" or "de" (optional, defaults to "en")
     * - validOnly: Only show currently valid offers (optional, defaults to true)
     * 
     * Output:
     * - offers: List of partner offers
     * - count: Number of offers
     * - voiceResponse: Voice-friendly offer listing
     */
    private Map<String, Object> getPartnerOffers(Map<String, Object> input) {
        String offerTypeStr = (String) input.get("offerType");
        String cardTierStr = (String) input.getOrDefault("cardTier", "STANDARD");
        String lang = (String) input.getOrDefault("language", "en");
        boolean validOnly = (Boolean) input.getOrDefault("validOnly", true);
        
        CardTier cardTier = CardTier.fromString(cardTierStr);
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        List<PartnerOffer> offers;
        
        if (offerTypeStr != null && !offerTypeStr.isBlank()) {
            OfferType offerType = OfferType.fromString(offerTypeStr);
            offers = catalog.getPartnerOffersByType(offerType).stream()
                    .filter(o -> !validOnly || o.isCurrentlyValid())
                    .filter(o -> eligibilityChecker.isEligible(o, cardTier))
                    .collect(Collectors.toList());
        } else {
            offers = benefitsService.getPartnerOffers(cardTier, validOnly);
        }
        
        // Build voice response
        StringBuilder voiceResponse = new StringBuilder();
        if (offers.isEmpty()) {
            voiceResponse.append(isGerman 
                ? "Derzeit sind keine Partnerangebote verfügbar."
                : "There are no partner offers available at this time.");
        } else {
            voiceResponse.append(isGerman 
                ? "Sie haben Zugang zu " + offers.size() + " Partnerangeboten. "
                : "You have access to " + offers.size() + " partner offers. ");
            
            // Highlight expiring soon
            List<PartnerOffer> expiringSoon = offers.stream()
                    .filter(PartnerOffer::isExpiringSoon)
                    .toList();
            
            if (!expiringSoon.isEmpty()) {
                voiceResponse.append(isGerman 
                    ? expiringSoon.size() + " laufen bald ab. "
                    : expiringSoon.size() + " expiring soon. ");
            }
            
            // List top offers
            for (int i = 0; i < Math.min(3, offers.size()); i++) {
                voiceResponse.append(offers.get(i).formatForVoiceBrief(lang)).append(". ");
            }
            
            if (offers.size() > 3) {
                voiceResponse.append(isGerman 
                    ? "Möchten Sie mehr Angebote hören?"
                    : "Would you like to hear more offers?");
            }
        }
        
        return Map.of(
            "success", true,
            "offers", offers.stream().map(PartnerOffer::toMap).collect(Collectors.toList()),
            "count", offers.size(),
            "voiceResponse", voiceResponse.toString()
        );
    }

    /**
     * Get contact information for a specific service.
     * 
     * Input:
     * - serviceId: The service ID to get contact for (optional)
     * - insuranceType: Get contact for insurance claim (optional)
     * - contactType: "EMERGENCY", "CARD_SERVICES", "CONCIERGE" (optional)
     * - language: "en" or "de" (optional, defaults to "en")
     * 
     * Output:
     * - contact: Contact details
     * - voiceResponse: Voice-friendly contact info
     */
    private Map<String, Object> getServiceContact(Map<String, Object> input) {
        String serviceId = (String) input.get("serviceId");
        String insuranceTypeStr = (String) input.get("insuranceType");
        String contactType = (String) input.get("contactType");
        String lang = (String) input.getOrDefault("language", "en");
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        ServiceContactService.ServiceContact contact = null;
        ServiceContactService.ClaimContact claimContact = null;
        
        // Priority: specific service > insurance claim > contact type
        if (serviceId != null) {
            Optional<ServiceContactService.ServiceContact> contactOpt = 
                    contactService.getContactForService(serviceId);
            if (contactOpt.isEmpty()) {
                return Map.of(
                    "success", false,
                    "error", "Service not found: " + serviceId,
                    "voiceResponse", isGerman 
                        ? "Dieser Service ist mir nicht bekannt."
                        : "I don't recognize that service."
                );
            }
            contact = contactOpt.get();
        } else if (insuranceTypeStr != null) {
            InsuranceType insuranceType = InsuranceType.fromString(insuranceTypeStr);
            if (insuranceType != null) {
                claimContact = contactService.getClaimContact(insuranceType, lang);
            }
        } else if (contactType != null) {
            contact = switch (contactType.toUpperCase()) {
                case "EMERGENCY" -> contactService.getEmergencyContact(lang);
                case "CARD_SERVICES" -> contactService.getCardServicesContact(lang);
                case "CONCIERGE" -> contactService.getConciergeContact(lang);
                default -> null;
            };
        }
        
        if (contact == null && claimContact == null) {
            return Map.of(
                "success", false,
                "error", "No contact specified",
                "voiceResponse", isGerman 
                    ? "Bitte geben Sie an, für welchen Service Sie Kontaktinformationen benötigen."
                    : "Please specify which service you need contact information for."
            );
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        
        if (claimContact != null) {
            result.put("claimContact", claimContact.toMap());
            result.put("voiceResponse", claimContact.formatForVoice(lang));
        } else {
            result.put("contact", contact.toMap());
            result.put("voiceResponse", contactService.formatContactForVoice(contact, lang));
        }
        
        return result;
    }
}
