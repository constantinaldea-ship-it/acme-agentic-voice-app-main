package com.voicebanking.agent.nonbanking.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stub implementation for insurance partner integration.
 * 
 * Returns simulated data for development and testing.
 * Production implementation would integrate with actual insurance partner APIs.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Component
@Profile("!production")
public class InsurancePartnerStub implements PartnerIntegration {
    private static final Logger log = LoggerFactory.getLogger(InsurancePartnerStub.class);

    private static final String PARTNER_ID = "axa-assistance";
    private static final String PARTNER_NAME = "AXA Assistance";

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
        // Simulate 98% availability for insurance services
        return ThreadLocalRandom.current().nextDouble() < 0.98;
    }

    @Override
    public Optional<Map<String, Object>> getCustomerData(String customerId) {
        log.debug("Fetching insurance data for customer: {}", customerId);
        
        if (customerId == null || customerId.isBlank()) {
            return Optional.empty();
        }

        Map<String, Object> data = new HashMap<>();
        data.put("policyNumber", "INS-" + Math.abs(customerId.hashCode() % 1000000));
        data.put("coverages", List.of("TRAVEL_MEDICAL", "PURCHASE_PROTECTION", "RENTAL_CAR_CDW"));
        data.put("activeSince", "2024-03-15");
        data.put("claimsThisYear", 0);
        data.put("claimsLifetime", ThreadLocalRandom.current().nextInt(0, 3));
        
        return Optional.of(data);
    }

    @Override
    public String generateDeepLink(String action, Map<String, String> parameters) {
        String baseUrl = "https://claims.axa-assistance.de/";
        
        return switch (action) {
            case "file_claim" -> baseUrl + "new-claim";
            case "claim_status" -> baseUrl + "status?claim=" + parameters.getOrDefault("claimId", "");
            case "documents" -> baseUrl + "documents";
            case "emergency" -> "tel:+498001002500";
            default -> "https://www.axa-assistance.de";
        };
    }

    @Override
    public Map<String, String> getContactInfo() {
        return Map.of(
            "emergencyPhone", "+49 800 100-2500",
            "claimsPhone", "+49 800 100-2501",
            "email", "claims@axa-assistance.de",
            "website", "www.axa-assistance.de",
            "available", "24/7"
        );
    }

    /**
     * Get claim instructions for a specific insurance type.
     * @param insuranceType the type of insurance
     * @param lang language code
     * @return claim instructions
     */
    public ClaimInstructions getClaimInstructions(String insuranceType, String lang) {
        boolean isGerman = "de".equalsIgnoreCase(lang);
        
        return switch (insuranceType.toUpperCase()) {
            case "TRAVEL_MEDICAL" -> new ClaimInstructions(
                isGerman ? "Auslandskrankenversicherung Schadensfall" : "Travel Medical Insurance Claim",
                isGerman 
                    ? List.of(
                        "Kontaktieren Sie die 24-Stunden-Hotline vor der Behandlung wenn möglich",
                        "Bewahren Sie alle Belege und ärztlichen Unterlagen auf",
                        "Melden Sie den Schaden innerhalb von 48 Stunden nach der Behandlung",
                        "Senden Sie das ausgefüllte Formular mit Unterlagen ein"
                    )
                    : List.of(
                        "Contact the 24-hour hotline before treatment if possible",
                        "Keep all receipts and medical documents",
                        "Report the claim within 48 hours of treatment",
                        "Submit the completed form with documentation"
                    ),
                "+49 800 100-2500",
                "claims@axa-assistance.de",
                48
            );
            case "PURCHASE_PROTECTION" -> new ClaimInstructions(
                isGerman ? "Einkaufsversicherung Schadensfall" : "Purchase Protection Claim",
                isGerman
                    ? List.of(
                        "Dokumentieren Sie den Schaden mit Fotos",
                        "Bewahren Sie die Originalquittung auf",
                        "Bei Diebstahl: Polizeibericht erstellen",
                        "Melden Sie den Schaden innerhalb von 30 Tagen"
                    )
                    : List.of(
                        "Document the damage with photos",
                        "Keep the original receipt",
                        "For theft: file a police report",
                        "Report the claim within 30 days"
                    ),
                "+49 800 100-2502",
                "purchase-claims@axa-assistance.de",
                30
            );
            case "RENTAL_CAR_CDW" -> new ClaimInstructions(
                isGerman ? "Mietwagen-Kasko Schadensfall" : "Rental Car CDW Claim",
                isGerman
                    ? List.of(
                        "Melden Sie den Schaden sofort der Mietwagenfirma",
                        "Erhalten Sie eine Kopie des Schadensberichts",
                        "Fotografieren Sie den Schaden",
                        "Melden Sie den Schaden innerhalb von 15 Tagen"
                    )
                    : List.of(
                        "Report damage to rental company immediately",
                        "Obtain a copy of the damage report",
                        "Photograph the damage",
                        "Report claim within 15 days"
                    ),
                "+49 800 100-2503",
                "rental-claims@axa-assistance.de",
                15
            );
            default -> new ClaimInstructions(
                isGerman ? "Allgemeiner Schadensfall" : "General Claim",
                isGerman
                    ? List.of(
                        "Kontaktieren Sie unsere Schadenshotline",
                        "Beschreiben Sie den Vorfall",
                        "Senden Sie alle relevanten Unterlagen"
                    )
                    : List.of(
                        "Contact our claims hotline",
                        "Describe the incident",
                        "Submit all relevant documentation"
                    ),
                "+49 800 100-2500",
                "claims@axa-assistance.de",
                30
            );
        };
    }

    /**
     * Record for claim instructions.
     */
    public record ClaimInstructions(
        String title,
        List<String> steps,
        String phone,
        String email,
        int deadlineDays
    ) {
        public String formatForVoice(String lang) {
            boolean isGerman = "de".equalsIgnoreCase(lang);
            StringBuilder sb = new StringBuilder();
            
            sb.append(title).append(". ");
            sb.append(isGerman ? "So melden Sie einen Schaden: " : "To file a claim: ");
            
            for (int i = 0; i < steps.size(); i++) {
                sb.append(isGerman ? "Schritt " : "Step ").append(i + 1).append(": ");
                sb.append(steps.get(i)).append(". ");
            }
            
            sb.append(isGerman 
                ? "Rufen Sie an unter " 
                : "Call us at ");
            sb.append(phone).append(".");
            
            return sb.toString();
        }
    }

    /**
     * Simulate checking if a customer has a specific coverage.
     */
    public boolean hasCoverage(String customerId, String coverageType) {
        return getCustomerData(customerId)
            .map(data -> {
                @SuppressWarnings("unchecked")
                List<String> coverages = (List<String>) data.get("coverages");
                return coverages != null && coverages.contains(coverageType.toUpperCase());
            })
            .orElse(false);
    }

    /**
     * Get coverage summary for customer.
     */
    public Optional<CoverageSummary> getCoverageSummary(String customerId) {
        return getCustomerData(customerId).map(data -> {
            @SuppressWarnings("unchecked")
            List<String> coverages = (List<String>) data.get("coverages");
            return new CoverageSummary(
                (String) data.get("policyNumber"),
                coverages != null ? coverages : List.of(),
                (String) data.get("activeSince"),
                (Integer) data.get("claimsThisYear")
            );
        });
    }

    /**
     * Summary record for coverage data.
     */
    public record CoverageSummary(
        String policyNumber,
        List<String> activeCoverages,
        String activeSince,
        int claimsThisYear
    ) {}
}
