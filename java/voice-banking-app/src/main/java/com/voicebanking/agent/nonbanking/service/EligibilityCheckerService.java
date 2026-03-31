package com.voicebanking.agent.nonbanking.service;

import com.voicebanking.agent.nonbanking.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for checking customer eligibility for non-banking services.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class EligibilityCheckerService {
    private static final Logger log = LoggerFactory.getLogger(EligibilityCheckerService.class);
    
    /**
     * Check if a customer with given card tier is eligible for a service.
     * @param service the service to check
     * @param customerCardTier the customer's card tier
     * @return true if eligible
     */
    public boolean isEligible(NonBankingService service, CardTier customerCardTier) {
        ServiceEligibility eligibility = service.getEligibility();
        
        if (eligibility == null || eligibility.isAllCustomers()) {
            return true;
        }
        
        if (customerCardTier == null) {
            log.debug("Customer has no card tier, checking if service {} allows all customers", 
                    service.getServiceId());
            return eligibility.isAllCustomers();
        }
        
        CardTier minTier = eligibility.getMinimumCardTier();
        if (minTier == null) {
            return true;
        }
        
        boolean eligible = customerCardTier.meetsRequirement(minTier);
        log.debug("Eligibility check for {} with tier {}: {}", 
                service.getServiceId(), customerCardTier, eligible);
        
        return eligible;
    }
    
    /**
     * Check eligibility with full customer context.
     * @param service the service to check
     * @param customerCardTier the customer's card tier
     * @param accountType the customer's account type
     * @param tenureYears years as customer
     * @param balance account balance
     * @return true if eligible
     */
    public boolean isEligible(NonBankingService service, CardTier customerCardTier,
                              String accountType, int tenureYears, double balance) {
        ServiceEligibility eligibility = service.getEligibility();
        
        if (eligibility == null || eligibility.isAllCustomers()) {
            return true;
        }
        
        return eligibility.isEligible(customerCardTier, accountType, tenureYears, balance);
    }
    
    /**
     * Get the reason why a customer is not eligible.
     * @param service the service
     * @param customerCardTier the customer's card tier
     * @param lang language code
     * @return ineligibility reason or null if eligible
     */
    public String getIneligibilityReason(NonBankingService service, CardTier customerCardTier, String lang) {
        if (isEligible(service, customerCardTier)) {
            return null;
        }
        
        ServiceEligibility eligibility = service.getEligibility();
        if (eligibility != null) {
            return eligibility.getIneligibilityReason(customerCardTier, lang);
        }
        
        return "de".equalsIgnoreCase(lang) 
            ? "Sie sind für diesen Service nicht berechtigt."
            : "You are not eligible for this service.";
    }
    
    /**
     * Get upgrade suggestion for an ineligible service.
     * @param service the service
     * @param customerCardTier the customer's current card tier
     * @param lang language code
     * @return upgrade suggestion or null
     */
    public String getUpgradeSuggestion(NonBankingService service, CardTier customerCardTier, String lang) {
        if (isEligible(service, customerCardTier)) {
            return null;
        }
        
        ServiceEligibility eligibility = service.getEligibility();
        if (eligibility != null) {
            return eligibility.getUpgradeSuggestion(customerCardTier, lang);
        }
        
        return null;
    }
    
    /**
     * Get eligibility status as a formatted response.
     * @param service the service
     * @param customerCardTier the customer's card tier
     * @param lang language code
     * @return eligibility status
     */
    public EligibilityStatus getEligibilityStatus(NonBankingService service, 
                                                   CardTier customerCardTier, String lang) {
        boolean eligible = isEligible(service, customerCardTier);
        String reason = eligible ? null : getIneligibilityReason(service, customerCardTier, lang);
        String upgrade = eligible ? null : getUpgradeSuggestion(service, customerCardTier, lang);
        
        return new EligibilityStatus(eligible, reason, upgrade);
    }
    
    /**
     * Record for eligibility status.
     */
    public record EligibilityStatus(
        boolean eligible,
        String reason,
        String upgradeSuggestion
    ) {
        public String formatForVoice(String lang) {
            if (eligible) {
                return "de".equalsIgnoreCase(lang)
                    ? "Sie sind für diesen Vorteil berechtigt."
                    : "You are eligible for this benefit.";
            }
            
            StringBuilder sb = new StringBuilder();
            if (reason != null) {
                sb.append(reason).append(" ");
            }
            if (upgradeSuggestion != null) {
                sb.append(upgradeSuggestion);
            }
            return sb.toString().trim();
        }
    }
}
