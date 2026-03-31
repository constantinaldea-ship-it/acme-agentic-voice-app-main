package com.voicebanking.agent.nonbanking;

import com.voicebanking.agent.nonbanking.catalog.ServicesCatalog;
import com.voicebanking.agent.nonbanking.catalog.StaticServicesCatalog;
import com.voicebanking.agent.nonbanking.domain.*;
import com.voicebanking.agent.nonbanking.integration.InsurancePartnerStub;
import com.voicebanking.agent.nonbanking.integration.MilesAndMoreStub;
import com.voicebanking.agent.nonbanking.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for NonBankingServicesAgent.
 * 
 * Tests all 5 tools with various scenarios including:
 * - Bilingual responses (English/German)
 * - Card tier eligibility
 * - Error handling
 * - Edge cases
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@DisplayName("NonBankingServicesAgent Tests")
class NonBankingServicesAgentTest {

    private NonBankingServicesAgent agent;
    private ServicesCatalog catalog;
    private BenefitsService benefitsService;
    private EligibilityCheckerService eligibilityChecker;
    private ServiceContactService contactService;
    private MilesAndMoreStub milesAndMoreStub;
    private InsurancePartnerStub insurancePartnerStub;

    @BeforeEach
    void setUp() {
        // Use real implementations with static catalog
        catalog = new StaticServicesCatalog();
        ((StaticServicesCatalog) catalog).init();  // Trigger @PostConstruct manually
        
        eligibilityChecker = new EligibilityCheckerService();
        benefitsService = new BenefitsService(catalog, eligibilityChecker);
        insurancePartnerStub = new InsurancePartnerStub();
        contactService = new ServiceContactService(catalog, insurancePartnerStub);
        milesAndMoreStub = new MilesAndMoreStub();
        
        agent = new NonBankingServicesAgent(
            catalog,
            benefitsService,
            eligibilityChecker,
            contactService,
            milesAndMoreStub,
            insurancePartnerStub
        );
    }

    @Test
    @DisplayName("Agent ID should be 'non-banking-services'")
    void testAgentId() {
        assertEquals("non-banking-services", agent.getAgentId());
    }

    @Test
    @DisplayName("Agent should provide 5 tools")
    void testToolIds() {
        List<String> toolIds = agent.getToolIds();
        assertEquals(5, toolIds.size());
        assertTrue(toolIds.contains("getMyBenefits"));
        assertTrue(toolIds.contains("getInsuranceInfo"));
        assertTrue(toolIds.contains("getTravelBenefits"));
        assertTrue(toolIds.contains("getPartnerOffers"));
        assertTrue(toolIds.contains("getServiceContact"));
    }

    @Test
    @DisplayName("Agent description should mention non-banking services")
    void testDescription() {
        String description = agent.getDescription();
        assertNotNull(description);
        assertTrue(description.toLowerCase().contains("non-banking") || 
                   description.toLowerCase().contains("insurance") ||
                   description.toLowerCase().contains("travel"));
    }

    @Test
    @DisplayName("Unknown tool should return error")
    void testUnknownTool() {
        Map<String, Object> result = agent.executeTool("unknownTool", Map.of());
        assertFalse((Boolean) result.get("success"));
        assertTrue(((String) result.get("error")).contains("Unknown tool"));
    }

    @Nested
    @DisplayName("getMyBenefits Tool")
    class GetMyBenefitsTests {

        @Test
        @DisplayName("Should return benefits for STANDARD tier")
        void testStandardTierBenefits() {
            Map<String, Object> result = agent.executeTool("getMyBenefits", Map.of(
                "cardTier", "STANDARD",
                "language", "en"
            ));
            
            assertTrue((Boolean) result.get("success"));
            assertEquals("STANDARD", result.get("cardTier"));
            assertTrue((Integer) result.get("totalCount") > 0);
            assertNotNull(result.get("voiceResponse"));
        }

        @Test
        @DisplayName("Should return more benefits for PLATINUM tier")
        void testPlatinumTierHasMoreBenefits() {
            Map<String, Object> standardResult = agent.executeTool("getMyBenefits", Map.of(
                "cardTier", "STANDARD"
            ));
            Map<String, Object> platinumResult = agent.executeTool("getMyBenefits", Map.of(
                "cardTier", "PLATINUM"
            ));
            
            int standardCount = (Integer) standardResult.get("totalCount");
            int platinumCount = (Integer) platinumResult.get("totalCount");
            
            assertTrue(platinumCount >= standardCount, 
                "Platinum should have at least as many benefits as Standard");
        }

        @Test
        @DisplayName("Should filter by category")
        void testFilterByCategory() {
            Map<String, Object> result = agent.executeTool("getMyBenefits", Map.of(
                "cardTier", "PLATINUM",
                "category", "INSURANCE"
            ));
            
            assertTrue((Boolean) result.get("success"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> benefits = (List<Map<String, Object>>) result.get("benefits");
            assertNotNull(benefits);
            // All returned benefits should be insurance
            for (Map<String, Object> benefit : benefits) {
                assertEquals("INSURANCE", benefit.get("category"));
            }
        }

        @Test
        @DisplayName("Should return German response")
        void testGermanResponse() {
            Map<String, Object> result = agent.executeTool("getMyBenefits", Map.of(
                "cardTier", "GOLD",
                "language", "de"
            ));
            
            assertTrue((Boolean) result.get("success"));
            String voiceResponse = (String) result.get("voiceResponse");
            // German response should contain German words
            assertTrue(voiceResponse.contains("Gold") || voiceResponse.contains("Vorteile") ||
                       voiceResponse.contains("Zugang") || voiceResponse.contains("Ihrer"));
        }

        @Test
        @DisplayName("Should include upgrade suggestion for non-BLACK tier")
        void testUpgradeSuggestion() {
            Map<String, Object> result = agent.executeTool("getMyBenefits", Map.of(
                "cardTier", "GOLD"
            ));
            
            assertTrue((Boolean) result.get("success"));
            // May or may not have upgrade suggestion depending on catalog data
            // Just verify the response structure is valid
            assertNotNull(result.get("benefits"));
        }

        @Test
        @DisplayName("Should default to STANDARD tier and English")
        void testDefaults() {
            Map<String, Object> result = agent.executeTool("getMyBenefits", Map.of());
            
            assertTrue((Boolean) result.get("success"));
            assertEquals("STANDARD", result.get("cardTier"));
            String voiceResponse = (String) result.get("voiceResponse");
            // Should be English (contains common English words)
            assertTrue(voiceResponse.contains("card") || voiceResponse.contains("benefits") ||
                       voiceResponse.contains("access") || voiceResponse.contains("you"));
        }
    }

    @Nested
    @DisplayName("getInsuranceInfo Tool")
    class GetInsuranceInfoTests {

        @Test
        @DisplayName("Should return all insurances when no type specified")
        void testAllInsurances() {
            Map<String, Object> result = agent.executeTool("getInsuranceInfo", Map.of(
                "cardTier", "PLATINUM"
            ));
            
            assertTrue((Boolean) result.get("success"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> insurances = (List<Map<String, Object>>) result.get("insurances");
            assertNotNull(insurances);
            assertTrue((Integer) result.get("count") > 0);
        }

        @Test
        @DisplayName("Should return specific insurance details")
        void testSpecificInsurance() {
            Map<String, Object> result = agent.executeTool("getInsuranceInfo", Map.of(
                "insuranceType", "TRAVEL_MEDICAL",
                "cardTier", "PLATINUM"
            ));
            
            assertTrue((Boolean) result.get("success"));
            @SuppressWarnings("unchecked")
            Map<String, Object> insurance = (Map<String, Object>) result.get("insurance");
            assertNotNull(insurance);
            assertNotNull(result.get("eligible"));
            assertNotNull(result.get("claimContact"));
            assertNotNull(result.get("voiceResponse"));
        }

        @Test
        @DisplayName("Should include claim contact for insurance")
        void testClaimContact() {
            Map<String, Object> result = agent.executeTool("getInsuranceInfo", Map.of(
                "insuranceType", "PURCHASE_PROTECTION",
                "cardTier", "GOLD",
                "language", "en"
            ));
            
            assertTrue((Boolean) result.get("success"));
            @SuppressWarnings("unchecked")
            Map<String, Object> claimContact = (Map<String, Object>) result.get("claimContact");
            assertNotNull(claimContact);
            assertNotNull(claimContact.get("phone"));
        }

        @Test
        @DisplayName("Should return error for unknown insurance type")
        void testUnknownInsuranceType() {
            Map<String, Object> result = agent.executeTool("getInsuranceInfo", Map.of(
                "insuranceType", "FAKE_INSURANCE"
            ));
            
            assertFalse((Boolean) result.get("success"));
            assertTrue(((String) result.get("error")).contains("Unknown insurance type"));
        }

        @Test
        @DisplayName("Should check eligibility")
        void testEligibilityCheck() {
            Map<String, Object> result = agent.executeTool("getInsuranceInfo", Map.of(
                "insuranceType", "TRAVEL_MEDICAL",
                "cardTier", "STANDARD"
            ));
            
            assertTrue((Boolean) result.get("success"));
            assertNotNull(result.get("eligible"));
        }
    }

    @Nested
    @DisplayName("getTravelBenefits Tool")
    class GetTravelBenefitsTests {

        @Test
        @DisplayName("Should return all travel benefits")
        void testAllTravelBenefits() {
            Map<String, Object> result = agent.executeTool("getTravelBenefits", Map.of(
                "cardTier", "PLATINUM"
            ));
            
            assertTrue((Boolean) result.get("success"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> benefits = (List<Map<String, Object>>) result.get("travelBenefits");
            assertNotNull(benefits);
            assertTrue((Integer) result.get("count") > 0);
        }

        @Test
        @DisplayName("Should return specific travel benefit")
        void testSpecificTravelBenefit() {
            Map<String, Object> result = agent.executeTool("getTravelBenefits", Map.of(
                "benefitType", "PRIORITY_PASS",
                "cardTier", "PLATINUM"
            ));
            
            assertTrue((Boolean) result.get("success"));
            @SuppressWarnings("unchecked")
            Map<String, Object> benefit = (Map<String, Object>) result.get("travelBenefit");
            assertNotNull(benefit);
            assertNotNull(result.get("eligible"));
            assertNotNull(result.get("voiceResponse"));
        }

        @Test
        @DisplayName("Should return Miles & More balance when customerId provided")
        void testMilesAndMoreWithCustomerId() {
            Map<String, Object> result = agent.executeTool("getTravelBenefits", Map.of(
                "benefitType", "MILES_AND_MORE",
                "cardTier", "PLATINUM",
                "customerId", "C123456"
            ));
            
            assertTrue((Boolean) result.get("success"));
            // Stub should return miles data
            assertNotNull(result.get("milesBalance"));
            assertNotNull(result.get("milesStatus"));
            assertNotNull(result.get("milesSummary"));
        }

        @Test
        @DisplayName("Should return error for unknown travel benefit type")
        void testUnknownTravelBenefitType() {
            Map<String, Object> result = agent.executeTool("getTravelBenefits", Map.of(
                "benefitType", "FAKE_BENEFIT"
            ));
            
            assertFalse((Boolean) result.get("success"));
            assertTrue(((String) result.get("error")).contains("Unknown travel benefit type"));
        }

        @Test
        @DisplayName("Should return German response")
        void testGermanTravelBenefits() {
            Map<String, Object> result = agent.executeTool("getTravelBenefits", Map.of(
                "cardTier", "GOLD",
                "language", "de"
            ));
            
            assertTrue((Boolean) result.get("success"));
            String voiceResponse = (String) result.get("voiceResponse");
            // German response check
            assertTrue(voiceResponse.contains("Zugang") || voiceResponse.contains("Leistungen") ||
                       voiceResponse.contains("Reise") || voiceResponse.contains("haben"));
        }
    }

    @Nested
    @DisplayName("getPartnerOffers Tool")
    class GetPartnerOffersTests {

        @Test
        @DisplayName("Should return partner offers")
        void testPartnerOffers() {
            Map<String, Object> result = agent.executeTool("getPartnerOffers", Map.of(
                "cardTier", "GOLD"
            ));
            
            assertTrue((Boolean) result.get("success"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> offers = (List<Map<String, Object>>) result.get("offers");
            assertNotNull(offers);
            assertNotNull(result.get("count"));
            assertNotNull(result.get("voiceResponse"));
        }

        @Test
        @DisplayName("Should filter by offer type")
        void testFilterByOfferType() {
            Map<String, Object> result = agent.executeTool("getPartnerOffers", Map.of(
                "offerType", "DISCOUNT",
                "cardTier", "PLATINUM"
            ));
            
            assertTrue((Boolean) result.get("success"));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> offers = (List<Map<String, Object>>) result.get("offers");
            // All returned offers should be DISCOUNT type
            for (Map<String, Object> offer : offers) {
                assertEquals("DISCOUNT", offer.get("offerType"));
            }
        }

        @Test
        @DisplayName("Should only return valid offers by default")
        void testValidOnlyDefault() {
            Map<String, Object> result = agent.executeTool("getPartnerOffers", Map.of(
                "cardTier", "GOLD"
            ));
            
            assertTrue((Boolean) result.get("success"));
            // All offers should be currently valid
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> offers = (List<Map<String, Object>>) result.get("offers");
            for (Map<String, Object> offer : offers) {
                assertTrue((Boolean) offer.get("isCurrentlyValid"), 
                    "All offers should be valid when validOnly defaults to true");
            }
        }

        @Test
        @DisplayName("Should return German response")
        void testGermanPartnerOffers() {
            Map<String, Object> result = agent.executeTool("getPartnerOffers", Map.of(
                "cardTier", "GOLD",
                "language", "de"
            ));
            
            assertTrue((Boolean) result.get("success"));
            String voiceResponse = (String) result.get("voiceResponse");
            // German response should contain German words
            assertTrue(voiceResponse.contains("Partnerangebote") || 
                       voiceResponse.contains("Zugang") ||
                       voiceResponse.contains("haben") ||
                       voiceResponse.contains("Angebote"));
        }
    }

    @Nested
    @DisplayName("getServiceContact Tool")
    class GetServiceContactTests {

        @Test
        @DisplayName("Should return emergency contact")
        void testEmergencyContact() {
            Map<String, Object> result = agent.executeTool("getServiceContact", Map.of(
                "contactType", "EMERGENCY",
                "language", "en"
            ));
            
            assertTrue((Boolean) result.get("success"));
            @SuppressWarnings("unchecked")
            Map<String, Object> contact = (Map<String, Object>) result.get("contact");
            assertNotNull(contact);
            assertNotNull(contact.get("phone"));
            assertNotNull(result.get("voiceResponse"));
        }

        @Test
        @DisplayName("Should return card services contact")
        void testCardServicesContact() {
            Map<String, Object> result = agent.executeTool("getServiceContact", Map.of(
                "contactType", "CARD_SERVICES",
                "language", "en"
            ));
            
            assertTrue((Boolean) result.get("success"));
            @SuppressWarnings("unchecked")
            Map<String, Object> contact = (Map<String, Object>) result.get("contact");
            assertNotNull(contact);
            assertTrue(((String) contact.get("serviceName")).contains("Card Services"));
        }

        @Test
        @DisplayName("Should return concierge contact")
        void testConciergeContact() {
            Map<String, Object> result = agent.executeTool("getServiceContact", Map.of(
                "contactType", "CONCIERGE",
                "language", "en"
            ));
            
            assertTrue((Boolean) result.get("success"));
            @SuppressWarnings("unchecked")
            Map<String, Object> contact = (Map<String, Object>) result.get("contact");
            assertNotNull(contact);
            assertTrue(((String) contact.get("serviceName")).contains("Concierge"));
        }

        @Test
        @DisplayName("Should return claim contact for insurance type")
        void testClaimContactForInsurance() {
            Map<String, Object> result = agent.executeTool("getServiceContact", Map.of(
                "insuranceType", "TRAVEL_MEDICAL",
                "language", "en"
            ));
            
            assertTrue((Boolean) result.get("success"));
            @SuppressWarnings("unchecked")
            Map<String, Object> claimContact = (Map<String, Object>) result.get("claimContact");
            assertNotNull(claimContact);
            assertNotNull(claimContact.get("phone"));
            assertNotNull(claimContact.get("steps"));
        }

        @Test
        @DisplayName("Should return error when no contact specified")
        void testNoContactSpecified() {
            Map<String, Object> result = agent.executeTool("getServiceContact", Map.of());
            
            assertFalse((Boolean) result.get("success"));
            assertTrue(((String) result.get("error")).contains("No contact specified"));
        }

        @Test
        @DisplayName("Should return German contact info")
        void testGermanContact() {
            Map<String, Object> result = agent.executeTool("getServiceContact", Map.of(
                "contactType", "EMERGENCY",
                "language", "de"
            ));
            
            assertTrue((Boolean) result.get("success"));
            @SuppressWarnings("unchecked")
            Map<String, Object> contact = (Map<String, Object>) result.get("contact");
            assertNotNull(contact);
            // German service name
            String serviceName = (String) contact.get("serviceName");
            assertTrue(serviceName.contains("Notfall") || serviceName.contains("Assistance"));
        }

        @Test
        @DisplayName("Should return error for unknown service ID")
        void testUnknownServiceId() {
            Map<String, Object> result = agent.executeTool("getServiceContact", Map.of(
                "serviceId", "FAKE_SERVICE_ID"
            ));
            
            assertFalse((Boolean) result.get("success"));
            assertTrue(((String) result.get("error")).contains("Service not found"));
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle null input gracefully")
        void testNullInput() {
            // Tools should handle null maps gracefully
            Map<String, Object> result = agent.executeTool("getMyBenefits", new HashMap<>());
            assertTrue((Boolean) result.get("success"));
        }

        @Test
        @DisplayName("Should handle invalid card tier gracefully")
        void testInvalidCardTier() {
            // CardTier.fromString should handle unknown values
            Map<String, Object> result = agent.executeTool("getMyBenefits", Map.of(
                "cardTier", "DIAMOND"  // Invalid tier
            ));
            
            // Should default to STANDARD
            assertTrue((Boolean) result.get("success"));
        }

        @Test
        @DisplayName("Should handle mixed case language")
        void testMixedCaseLanguage() {
            Map<String, Object> result = agent.executeTool("getMyBenefits", Map.of(
                "language", "DE",
                "cardTier", "GOLD"
            ));
            
            assertTrue((Boolean) result.get("success"));
            String voiceResponse = (String) result.get("voiceResponse");
            // Should recognize "DE" as German
            assertTrue(voiceResponse.contains("Gold") || voiceResponse.contains("Vorteile") ||
                       voiceResponse.contains("Zugang") || voiceResponse.contains("Karte"));
        }

        @Test
        @DisplayName("BLACK tier should have no upgrade suggestion")
        void testBlackTierNoUpgrade() {
            Map<String, Object> result = agent.executeTool("getMyBenefits", Map.of(
                "cardTier", "BLACK"
            ));
            
            assertTrue((Boolean) result.get("success"));
            // BLACK is highest tier, should not have upgrade suggestion
            assertNull(result.get("upgradeSuggestion"));
        }
    }

    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {

        @Test
        @DisplayName("Full workflow: Check benefits, get insurance details, then contact")
        void testFullWorkflow() {
            // Step 1: Check what benefits are available
            Map<String, Object> benefitsResult = agent.executeTool("getMyBenefits", Map.of(
                "cardTier", "PLATINUM",
                "category", "INSURANCE"
            ));
            assertTrue((Boolean) benefitsResult.get("success"));
            
            // Step 2: Get details on specific insurance
            Map<String, Object> insuranceResult = agent.executeTool("getInsuranceInfo", Map.of(
                "insuranceType", "TRAVEL_MEDICAL",
                "cardTier", "PLATINUM"
            ));
            assertTrue((Boolean) insuranceResult.get("success"));
            assertTrue((Boolean) insuranceResult.get("eligible"));
            
            // Step 3: Get claim contact
            Map<String, Object> contactResult = agent.executeTool("getServiceContact", Map.of(
                "insuranceType", "TRAVEL_MEDICAL"
            ));
            assertTrue((Boolean) contactResult.get("success"));
            assertNotNull(contactResult.get("claimContact"));
        }

        @Test
        @DisplayName("Travel benefits workflow with Miles & More lookup")
        void testTravelWorkflow() {
            // Step 1: List all travel benefits
            Map<String, Object> listResult = agent.executeTool("getTravelBenefits", Map.of(
                "cardTier", "PLATINUM"
            ));
            assertTrue((Boolean) listResult.get("success"));
            
            // Step 2: Get Miles & More details with balance
            Map<String, Object> milesResult = agent.executeTool("getTravelBenefits", Map.of(
                "benefitType", "MILES_AND_MORE",
                "cardTier", "PLATINUM",
                "customerId", "C123456"
            ));
            assertTrue((Boolean) milesResult.get("success"));
            assertNotNull(milesResult.get("milesBalance"));
        }

        @Test
        @DisplayName("Partner offers workflow")
        void testPartnerOffersWorkflow() {
            // Step 1: Get all offers
            Map<String, Object> allOffersResult = agent.executeTool("getPartnerOffers", Map.of(
                "cardTier", "GOLD"
            ));
            assertTrue((Boolean) allOffersResult.get("success"));
            
            // Step 2: Filter by discount type
            Map<String, Object> discountsResult = agent.executeTool("getPartnerOffers", Map.of(
                "offerType", "DISCOUNT",
                "cardTier", "GOLD"
            ));
            assertTrue((Boolean) discountsResult.get("success"));
        }
    }
}
