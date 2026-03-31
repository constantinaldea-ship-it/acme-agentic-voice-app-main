package com.voicebanking.bfa.controller;

import com.voicebanking.bfa.dto.*;
import com.voicebanking.bfa.annotation.Audited;
import com.voicebanking.bfa.annotation.RequiresConsent;
import com.voicebanking.bfa.annotation.RequiresLegitimation;
import com.voicebanking.bfa.service.NonBankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for non-banking services.
 * 
 * <p>Exposes endpoints for retrieving bank-offered third-party services including
 * insurance partnerships, travel benefits, lifestyle perks, and partner offers.</p>
 * 
 * <h2>Endpoints</h2>
 * <table>
 *   <tr><th>Method</th><th>Endpoint</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/v1/services/benefits</td><td>Get all benefits by card tier</td></tr>
 *   <tr><td>GET</td><td>/api/v1/services/insurance</td><td>Get insurance information</td></tr>
 *   <tr><td>GET</td><td>/api/v1/services/travel</td><td>Get travel benefits</td></tr>
 *   <tr><td>GET</td><td>/api/v1/services/offers</td><td>Get partner offers</td></tr>
 *   <tr><td>GET</td><td>/api/v1/services/contacts</td><td>Get service contacts</td></tr>
 * </table>
 * 
 * <p>Migrated from NonBankingServicesAgent per ADR-BFA-003.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-03
 * @see NonBankingService
 */
@RestController
@RequestMapping("/api/v1/services")
public class NonBankingController {
    
    private static final Logger log = LoggerFactory.getLogger(NonBankingController.class);
    
    private final NonBankingService nonBankingService;
    
    public NonBankingController(NonBankingService nonBankingService) {
        this.nonBankingService = nonBankingService;
    }
    
    /**
     * Get all benefits for a customer based on their card tier.
     * 
     * <p>Returns a list of all available benefits, with eligibility status
     * based on the customer's card tier. Benefits are grouped by category
     * and include upgrade suggestions for locked benefits.</p>
     * 
     * <h3>Example Request</h3>
     * <pre>GET /api/v1/services/benefits?cardTier=GOLD&amp;category=TRAVEL&amp;lang=en</pre>
     * 
     * @param cardTier Customer's card tier (STANDARD, GOLD, PLATINUM, BLACK)
     * @param category Optional category filter (INSURANCE, TRAVEL, LIFESTYLE, PARTNERS, DIGITAL)
     * @param lang Language for voice response (en, de)
     * @return BenefitsDto with all benefits and eligibility status
     */
    @GetMapping("/benefits")
    @RequiresConsent({"VIEW_BENEFITS", "AI_INTERACTION"})
    @RequiresLegitimation(scope = "benefits:read")
    @Audited(operation = "GET_BENEFITS")
    public ResponseEntity<BenefitsDto> getBenefits(
            @RequestParam(defaultValue = "STANDARD") String cardTier,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "en") String lang
    ) {
        log.info("GET /api/v1/services/benefits tier={} category={} lang={}", cardTier, category, lang);
        
        BenefitsDto benefits = nonBankingService.getMyBenefits(cardTier, category, lang);
        
        return ResponseEntity.ok(benefits);
    }
    
    /**
     * Get insurance information.
     * 
     * <p>Returns details about insurance coverage available through the bank,
     * including coverage limits, included/excluded items, and claim procedures.</p>
     * 
     * <h3>Example Request</h3>
     * <pre>GET /api/v1/services/insurance?type=TRAVEL_MEDICAL&amp;cardTier=GOLD&amp;lang=en</pre>
     * 
     * @param type Optional insurance type filter (TRAVEL_MEDICAL, TRIP_CANCELLATION, PURCHASE_PROTECTION, RENTAL_CAR_CDW)
     * @param cardTier Customer's card tier for eligibility check
     * @param lang Language for voice response (en, de)
     * @return InsuranceInfoDto with coverage details
     */
    @GetMapping("/insurance")
    @RequiresConsent({"VIEW_INSURANCE", "AI_INTERACTION"})
    @RequiresLegitimation(scope = "insurance:read")
    @Audited(operation = "GET_INSURANCE_INFO")
    public ResponseEntity<InsuranceInfoDto> getInsuranceInfo(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "STANDARD") String cardTier,
            @RequestParam(defaultValue = "en") String lang
    ) {
        log.info("GET /api/v1/services/insurance type={} tier={} lang={}", type, cardTier, lang);
        
        InsuranceInfoDto insuranceInfo = nonBankingService.getInsuranceInfo(type, cardTier, lang);
        
        return ResponseEntity.ok(insuranceInfo);
    }
    
    /**
     * Get travel benefits.
     * 
     * <p>Returns information about travel-related benefits including lounge access,
     * miles programs, concierge services, and travel discounts.</p>
     * 
     * <h3>Example Request</h3>
     * <pre>GET /api/v1/services/travel?benefitType=AIRPORT_LOUNGE&amp;cardTier=PLATINUM&amp;lang=en</pre>
     * 
     * @param benefitType Optional benefit type filter (AIRPORT_LOUNGE, MILES_AND_MORE, TRAVEL_CONCIERGE, etc.)
     * @param cardTier Customer's card tier for eligibility check
     * @param customerId Optional customer ID for retrieving Miles & More balance
     * @param lang Language for voice response (en, de)
     * @return TravelBenefitsDto with travel benefits and miles info
     */
    @GetMapping("/travel")
    @RequiresConsent({"VIEW_TRAVEL_BENEFITS", "AI_INTERACTION"})
    @RequiresLegitimation(scope = "travel:read")
    @Audited(operation = "GET_TRAVEL_BENEFITS")
    public ResponseEntity<TravelBenefitsDto> getTravelBenefits(
            @RequestParam(required = false) String benefitType,
            @RequestParam(defaultValue = "STANDARD") String cardTier,
            @RequestParam(required = false) String customerId,
            @RequestParam(defaultValue = "en") String lang
    ) {
        log.info("GET /api/v1/services/travel type={} tier={} customerId={} lang={}", 
            benefitType, cardTier, customerId != null ? "***" : null, lang);
        
        TravelBenefitsDto travelBenefits = nonBankingService.getTravelBenefits(
            benefitType, cardTier, customerId, lang);
        
        return ResponseEntity.ok(travelBenefits);
    }
    
    /**
     * Get partner offers.
     * 
     * <p>Returns current promotional offers from partner merchants including
     * discounts, cashback, and bonus point opportunities.</p>
     * 
     * <h3>Example Request</h3>
     * <pre>GET /api/v1/services/offers?offerType=DISCOUNT&amp;cardTier=GOLD&amp;validOnly=true&amp;lang=en</pre>
     * 
     * @param offerType Optional offer type filter (DISCOUNT, CASHBACK, BONUS_POINTS, EXCLUSIVE_ACCESS)
     * @param cardTier Customer's card tier for eligibility check
     * @param validOnly If true, only return currently valid offers
     * @param lang Language for voice response (en, de)
     * @return PartnerOffersDto with available offers
     */
    @GetMapping("/offers")
    @RequiresConsent({"VIEW_PARTNER_OFFERS", "AI_INTERACTION"})
    @RequiresLegitimation(scope = "offers:read")
    @Audited(operation = "GET_PARTNER_OFFERS")
    public ResponseEntity<PartnerOffersDto> getPartnerOffers(
            @RequestParam(required = false) String offerType,
            @RequestParam(defaultValue = "STANDARD") String cardTier,
            @RequestParam(defaultValue = "true") boolean validOnly,
            @RequestParam(defaultValue = "en") String lang
    ) {
        log.info("GET /api/v1/services/offers type={} tier={} validOnly={} lang={}", 
            offerType, cardTier, validOnly, lang);
        
        PartnerOffersDto offers = nonBankingService.getPartnerOffers(offerType, cardTier, validOnly, lang);
        
        return ResponseEntity.ok(offers);
    }
    
    /**
     * Get service contact information.
     * 
     * <p>Returns contact details for bank services and partner support,
     * including emergency hotlines, customer service, and claims contacts.</p>
     * 
     * <h3>Example Request</h3>
     * <pre>GET /api/v1/services/contacts?contactType=EMERGENCY&amp;lang=en</pre>
     * 
     * @param contactType Optional contact type filter (EMERGENCY, CARD_SERVICES, CONCIERGE, INSURANCE_CLAIMS, etc.)
     * @param serviceId Optional service ID for specific service contact
     * @param lang Language for voice response (en, de)
     * @return ServiceContactDto with contact information
     */
    @GetMapping("/contacts")
    @RequiresConsent({"VIEW_SERVICE_CONTACTS", "AI_INTERACTION"})
    @RequiresLegitimation(scope = "contacts:read")
    @Audited(operation = "GET_SERVICE_CONTACTS")
    public ResponseEntity<ServiceContactDto> getServiceContacts(
            @RequestParam(required = false) String contactType,
            @RequestParam(required = false) String serviceId,
            @RequestParam(defaultValue = "en") String lang
    ) {
        log.info("GET /api/v1/services/contacts type={} serviceId={} lang={}", contactType, serviceId, lang);
        
        ServiceContactDto contacts = nonBankingService.getServiceContact(contactType, serviceId, lang);
        
        return ResponseEntity.ok(contacts);
    }
}
