package com.voicebanking.agent.nonbanking.service;

import com.voicebanking.agent.nonbanking.catalog.ServicesCatalog;
import com.voicebanking.agent.nonbanking.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing and retrieving customer benefits.
 * 
 * Handles benefit lookups, eligibility filtering, and voice formatting.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class BenefitsService {
    private static final Logger log = LoggerFactory.getLogger(BenefitsService.class);
    
    private final ServicesCatalog catalog;
    private final EligibilityCheckerService eligibilityChecker;
    
    public BenefitsService(ServicesCatalog catalog, EligibilityCheckerService eligibilityChecker) {
        this.catalog = catalog;
        this.eligibilityChecker = eligibilityChecker;
    }
    
    /**
     * Get all benefits available to a customer based on their card tier.
     * @param cardTier the customer's card tier
     * @return list of eligible benefits organized by category
     */
    public CustomerBenefits getBenefitsForCustomer(CardTier cardTier) {
        log.debug("Getting benefits for card tier: {}", cardTier);
        
        List<NonBankingService> allEligible = catalog.getEligibleServices(cardTier);
        
        Map<ServiceCategory, List<NonBankingService>> byCategory = allEligible.stream()
                .collect(Collectors.groupingBy(NonBankingService::getCategory));
        
        return new CustomerBenefits(cardTier, byCategory, allEligible.size());
    }
    
    /**
     * Get benefits summary for voice output.
     * @param cardTier the customer's card tier
     * @param lang language code
     * @return voice-friendly benefits summary
     */
    public String getBenefitsSummaryForVoice(CardTier cardTier, String lang) {
        CustomerBenefits benefits = getBenefitsForCustomer(cardTier);
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        StringBuilder sb = new StringBuilder();
        
        sb.append(isGerman 
            ? "Mit Ihrer " + cardTier.getName(lang) + " Karte haben Sie Zugang zu " + benefits.totalCount() + " Vorteilen. "
            : "With your " + cardTier.getName(lang) + " card, you have access to " + benefits.totalCount() + " benefits. ");
        
        // Summarize by category
        for (ServiceCategory category : ServiceCategory.values()) {
            List<NonBankingService> services = benefits.byCategory().get(category);
            if (services != null && !services.isEmpty()) {
                sb.append(category.getName(lang)).append(": ");
                sb.append(services.size());
                sb.append(isGerman ? " Leistungen. " : " services. ");
            }
        }
        
        sb.append(isGerman 
            ? "Möchten Sie Details zu einer bestimmten Kategorie?"
            : "Would you like details on a specific category?");
        
        return sb.toString();
    }
    
    /**
     * Get insurance benefits for customer.
     * @param cardTier the customer's card tier
     * @return list of eligible insurance coverages
     */
    public List<InsuranceCoverage> getInsuranceBenefits(CardTier cardTier) {
        return catalog.getInsuranceCoverages().stream()
                .filter(ins -> eligibilityChecker.isEligible(ins, cardTier))
                .collect(Collectors.toList());
    }
    
    /**
     * Get travel benefits for customer.
     * @param cardTier the customer's card tier
     * @return list of eligible travel benefits
     */
    public List<TravelBenefit> getTravelBenefits(CardTier cardTier) {
        return catalog.getTravelBenefits().stream()
                .filter(tb -> eligibilityChecker.isEligible(tb, cardTier))
                .collect(Collectors.toList());
    }
    
    /**
     * Get partner offers for customer.
     * @param cardTier the customer's card tier
     * @param validOnly only return currently valid offers
     * @return list of eligible partner offers
     */
    public List<PartnerOffer> getPartnerOffers(CardTier cardTier, boolean validOnly) {
        List<PartnerOffer> offers = validOnly 
            ? catalog.getValidPartnerOffers() 
            : catalog.getPartnerOffers();
        
        return offers.stream()
                .filter(offer -> eligibilityChecker.isEligible(offer, cardTier))
                .collect(Collectors.toList());
    }
    
    /**
     * Get services that customer is NOT eligible for, for upgrade messaging.
     * @param cardTier the customer's current card tier
     * @return list of services requiring upgrade
     */
    public List<NonBankingService> getUpgradeServices(CardTier cardTier) {
        return catalog.getAllServices().stream()
                .filter(s -> !eligibilityChecker.isEligible(s, cardTier))
                .collect(Collectors.toList());
    }
    
    /**
     * Get upgrade suggestion for customer.
     * @param cardTier the customer's current card tier
     * @param lang language code
     * @return upgrade suggestion message or null if at highest tier
     */
    public String getUpgradeSuggestion(CardTier cardTier, String lang) {
        CardTier nextTier = cardTier.getNextTier();
        if (nextTier == null) {
            return null;  // Already at highest tier
        }
        
        List<NonBankingService> upgradeBenefits = catalog.getAllServices().stream()
                .filter(s -> {
                    ServiceEligibility elig = s.getEligibility();
                    if (elig == null || elig.isAllCustomers()) return false;
                    CardTier min = elig.getMinimumCardTier();
                    return min == nextTier;  // Services that become available at next tier
                })
                .collect(Collectors.toList());
        
        if (upgradeBenefits.isEmpty()) {
            return null;
        }
        
        boolean isGerman = "de".equalsIgnoreCase(lang);
        StringBuilder sb = new StringBuilder();
        
        sb.append(isGerman 
            ? "Mit einem Upgrade auf die " + nextTier.getName(lang) + " Karte erhalten Sie zusätzlich: "
            : "With an upgrade to the " + nextTier.getName(lang) + " card, you would also get: ");
        
        int count = Math.min(3, upgradeBenefits.size());
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(", ");
            sb.append(upgradeBenefits.get(i).getName(lang));
        }
        
        if (upgradeBenefits.size() > 3) {
            sb.append(isGerman 
                ? ", und " + (upgradeBenefits.size() - 3) + " weitere."
                : ", and " + (upgradeBenefits.size() - 3) + " more.");
        }
        
        return sb.toString();
    }
    
    /**
     * Format a list of services for voice output.
     * @param services the services to format
     * @param lang language code
     * @return voice-friendly list
     */
    public String formatServicesForVoice(List<? extends NonBankingService> services, String lang) {
        if (services.isEmpty()) {
            return "de".equalsIgnoreCase(lang) 
                ? "Keine Leistungen in dieser Kategorie verfügbar."
                : "No services available in this category.";
        }
        
        boolean isGerman = "de".equalsIgnoreCase(lang);
        StringBuilder sb = new StringBuilder();
        
        sb.append(isGerman 
            ? "Sie haben Zugang zu " + services.size() + " Leistungen: "
            : "You have access to " + services.size() + " services: ");
        
        for (int i = 0; i < Math.min(5, services.size()); i++) {
            if (i > 0 && i == Math.min(5, services.size()) - 1) {
                sb.append(isGerman ? ", und " : ", and ");
            } else if (i > 0) {
                sb.append(", ");
            }
            sb.append(services.get(i).getName(lang));
        }
        
        if (services.size() > 5) {
            sb.append(isGerman 
                ? ", plus " + (services.size() - 5) + " weitere."
                : ", plus " + (services.size() - 5) + " more.");
        } else {
            sb.append(".");
        }
        
        sb.append(isGerman 
            ? " Möchten Sie Details zu einem bestimmten Vorteil?"
            : " Would you like details on a specific benefit?");
        
        return sb.toString();
    }
    
    /**
     * Record containing customer benefits organized by category.
     */
    public record CustomerBenefits(
        CardTier cardTier,
        Map<ServiceCategory, List<NonBankingService>> byCategory,
        int totalCount
    ) {
        public List<NonBankingService> getInsurance() {
            return byCategory.getOrDefault(ServiceCategory.INSURANCE, List.of());
        }
        
        public List<NonBankingService> getTravel() {
            return byCategory.getOrDefault(ServiceCategory.TRAVEL, List.of());
        }
        
        public List<NonBankingService> getLifestyle() {
            return byCategory.getOrDefault(ServiceCategory.LIFESTYLE, List.of());
        }
        
        public List<NonBankingService> getPartners() {
            return byCategory.getOrDefault(ServiceCategory.PARTNERS, List.of());
        }
        
        public List<NonBankingService> getDigital() {
            return byCategory.getOrDefault(ServiceCategory.DIGITAL, List.of());
        }
        
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("cardTier", cardTier.name());
            map.put("totalCount", totalCount);
            
            Map<String, List<Map<String, Object>>> categories = new HashMap<>();
            byCategory.forEach((cat, services) -> 
                categories.put(cat.name(), services.stream()
                    .map(NonBankingService::toBriefMap)
                    .collect(Collectors.toList())));
            map.put("byCategory", categories);
            
            return map;
        }
    }
}
