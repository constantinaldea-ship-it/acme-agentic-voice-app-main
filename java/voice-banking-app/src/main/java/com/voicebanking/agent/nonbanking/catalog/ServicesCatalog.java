package com.voicebanking.agent.nonbanking.catalog;

import com.voicebanking.agent.nonbanking.domain.*;

import java.util.List;
import java.util.Optional;

/**
 * Interface for the non-banking services catalog.
 * 
 * Provides access to all non-banking services offered by Acme Bank
 * through partner relationships.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public interface ServicesCatalog {

    /**
     * Get all active services.
     * @return list of all active non-banking services
     */
    List<NonBankingService> getAllServices();

    /**
     * Get services by category.
     * @param category the service category
     * @return list of services in that category
     */
    List<NonBankingService> getServicesByCategory(ServiceCategory category);

    /**
     * Get a service by ID.
     * @param serviceId the service identifier
     * @return the service if found
     */
    Optional<NonBankingService> getServiceById(String serviceId);

    /**
     * Get all insurance coverages.
     * @return list of insurance services
     */
    List<InsuranceCoverage> getInsuranceCoverages();

    /**
     * Get insurance coverage by type.
     * @param type the insurance type
     * @return the insurance coverage if found
     */
    Optional<InsuranceCoverage> getInsuranceByType(InsuranceType type);

    /**
     * Get all travel benefits.
     * @return list of travel benefits
     */
    List<TravelBenefit> getTravelBenefits();

    /**
     * Get travel benefit by type.
     * @param type the travel benefit type
     * @return the travel benefit if found
     */
    Optional<TravelBenefit> getTravelBenefitByType(TravelBenefitType type);

    /**
     * Get all partner offers.
     * @return list of partner offers
     */
    List<PartnerOffer> getPartnerOffers();

    /**
     * Get currently valid partner offers.
     * @return list of valid offers
     */
    List<PartnerOffer> getValidPartnerOffers();

    /**
     * Get partner offers by type.
     * @param type the offer type
     * @return list of offers of that type
     */
    List<PartnerOffer> getPartnerOffersByType(OfferType type);

    /**
     * Get services eligible for a customer with given card tier.
     * @param cardTier the customer's card tier
     * @return list of eligible services
     */
    List<NonBankingService> getEligibleServices(CardTier cardTier);

    /**
     * Search services by query.
     * @param query the search query
     * @return list of matching services
     */
    List<NonBankingService> searchServices(String query);

    /**
     * Get total number of services.
     * @return count of all services
     */
    int getServiceCount();

    /**
     * Get services count by category.
     * @param category the service category
     * @return count of services in category
     */
    int getServiceCountByCategory(ServiceCategory category);
}
