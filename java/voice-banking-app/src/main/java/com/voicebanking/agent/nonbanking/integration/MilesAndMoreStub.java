package com.voicebanking.agent.nonbanking.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stub implementation for Miles & More partner integration.
 * 
 * Returns simulated data for development and testing.
 * Production implementation would integrate with Lufthansa Miles & More API.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Component
@Profile("!production")
public class MilesAndMoreStub implements PartnerIntegration {
    private static final Logger log = LoggerFactory.getLogger(MilesAndMoreStub.class);

    private static final String PARTNER_ID = "miles-and-more";
    private static final String PARTNER_NAME = "Miles & More";

    @Override
    public String getPartnerId() {
        return PARTNER_ID;
    }

    @Override
    public String getPartnerName() {
        return PARTNER_NAME;
    }

    @Override
    public boolean isAvailable() {
        // Simulate 95% availability
        return ThreadLocalRandom.current().nextDouble() < 0.95;
    }

    @Override
    public Optional<Map<String, Object>> getCustomerData(String customerId) {
        log.debug("Fetching Miles & More data for customer: {}", customerId);
        
        if (customerId == null || customerId.isBlank()) {
            return Optional.empty();
        }

        // Generate simulated data
        int baseBalance = ThreadLocalRandom.current().nextInt(5000, 150000);
        int statusMiles = ThreadLocalRandom.current().nextInt(0, 100000);
        
        Map<String, Object> data = new HashMap<>();
        data.put("membershipNumber", "99" + customerId.hashCode() % 10000000);
        data.put("awardMiles", baseBalance);
        data.put("statusMiles", statusMiles);
        data.put("status", determineStatus(statusMiles));
        data.put("statusValidUntil", "2027-02-28");
        data.put("expiringMiles", baseBalance / 10);
        data.put("expiringDate", "2026-12-31");
        data.put("lastActivity", "2026-01-15");
        
        return Optional.of(data);
    }

    private String determineStatus(int statusMiles) {
        if (statusMiles >= 100000) return "HON Circle";
        if (statusMiles >= 35000) return "Senator";
        if (statusMiles >= 10000) return "Frequent Traveller";
        return "Member";
    }

    @Override
    public String generateDeepLink(String action, Map<String, String> parameters) {
        String baseUrl = "milesandmore://";
        
        return switch (action) {
            case "balance" -> baseUrl + "balance";
            case "redeem" -> baseUrl + "redeem";
            case "earn" -> baseUrl + "earn";
            case "status" -> baseUrl + "status";
            case "shop" -> "https://www.worldshop.eu";
            default -> baseUrl + "home";
        };
    }

    @Override
    public Map<String, String> getContactInfo() {
        return Map.of(
            "phone", "+49 69 6600-1234",
            "email", "service@miles-and-more.com",
            "website", "www.miles-and-more.com",
            "app", "Miles & More App"
        );
    }

    /**
     * Get customer miles balance summary.
     * @param customerId the customer ID
     * @return miles summary or empty if not found
     */
    public Optional<MilesSummary> getMilesSummary(String customerId) {
        return getCustomerData(customerId).map(data -> new MilesSummary(
            (String) data.get("membershipNumber"),
            (Integer) data.get("awardMiles"),
            (Integer) data.get("statusMiles"),
            (String) data.get("status"),
            (Integer) data.get("expiringMiles"),
            (String) data.get("expiringDate")
        ));
    }

    /**
     * Summary record for Miles & More data.
     */
    public record MilesSummary(
        String membershipNumber,
        int awardMiles,
        int statusMiles,
        String status,
        int expiringMiles,
        String expiringDate
    ) {
        public String formatForVoice(String lang) {
            boolean isGerman = "de".equalsIgnoreCase(lang);
            if (isGerman) {
                return String.format(
                    "Ihr Miles & More Kontostand: %,d Prämienmeilen. Status: %s. " +
                    "%,d Meilen verfallen am %s.",
                    awardMiles, status, expiringMiles, expiringDate
                );
            }
            return String.format(
                "Your Miles & More balance: %,d award miles. Status: %s. " +
                "%,d miles expiring on %s.",
                awardMiles, status, expiringMiles, expiringDate
            );
        }
    }
}
