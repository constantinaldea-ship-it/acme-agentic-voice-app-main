package com.voicebanking.agent.nonbanking.integration;

import java.util.Map;
import java.util.Optional;

/**
 * Interface for partner service integrations.
 * 
 * Defines contract for external partner API integrations.
 * Production implementations would connect to actual partner APIs.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public interface PartnerIntegration {

    /**
     * Get the partner identifier.
     * @return unique partner ID
     */
    String getPartnerId();

    /**
     * Get the partner name.
     * @return display name of the partner
     */
    String getPartnerName();

    /**
     * Check if the partner integration is available.
     * @return true if partner API is reachable
     */
    boolean isAvailable();

    /**
     * Get customer data from the partner.
     * @param customerId the customer identifier
     * @return partner-specific customer data
     */
    Optional<Map<String, Object>> getCustomerData(String customerId);

    /**
     * Generate a deep link to partner app/website.
     * @param action the action to deep link to
     * @param parameters additional parameters
     * @return the deep link URL
     */
    String generateDeepLink(String action, Map<String, String> parameters);

    /**
     * Get partner contact information.
     * @return contact details map
     */
    Map<String, String> getContactInfo();
}
