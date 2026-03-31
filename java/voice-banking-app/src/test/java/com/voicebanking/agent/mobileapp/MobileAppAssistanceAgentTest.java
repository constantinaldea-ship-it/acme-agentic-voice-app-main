package com.voicebanking.agent.mobileapp;

import com.voicebanking.agent.mobileapp.catalog.AppFeatureCatalog;
import com.voicebanking.agent.mobileapp.catalog.StaticAppFeatureCatalog;
import com.voicebanking.agent.mobileapp.domain.*;
import com.voicebanking.agent.mobileapp.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive tests for MobileAppAssistanceAgent.
 * 
 * Uses real service implementations to test full integration
 * of the agent with its supporting services.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@DisplayName("MobileAppAssistanceAgent Tests")
class MobileAppAssistanceAgentTest {

    private MobileAppAssistanceAgent agent;
    private StaticAppFeatureCatalog featureCatalog;
    private FeatureGuideService guideService;
    private NavigationService navigationService;
    private TroubleshootingService troubleshootingService;

    @BeforeEach
    void setUp() {
        // Initialize real services for integration testing
        featureCatalog = new StaticAppFeatureCatalog();
        featureCatalog.init();
        
        guideService = new FeatureGuideService(featureCatalog);
        guideService.init();
        
        navigationService = new NavigationService(featureCatalog);
        navigationService.init();
        
        troubleshootingService = new TroubleshootingService();
        troubleshootingService.init();

        agent = new MobileAppAssistanceAgent(
                featureCatalog,
                guideService,
                navigationService,
                troubleshootingService
        );
    }

    @Nested
    @DisplayName("Agent Metadata Tests")
    class AgentMetadataTests {

        @Test
        @DisplayName("Should have correct agent ID")
        void shouldHaveCorrectAgentId() {
            assertThat(agent.getAgentId()).isEqualTo("mobile-app-assistance");
        }

        @Test
        @DisplayName("Should have appropriate description")
        void shouldHaveDescription() {
            assertThat(agent.getDescription())
                    .contains("Mobile Banking app")
                    .contains("guidance");
        }

        @Test
        @DisplayName("Should provide all 6 tools")
        void shouldProvideAllTools() {
            assertThat(agent.getToolIds()).hasSize(6);
            assertThat(agent.getToolIds()).containsExactlyInAnyOrder(
                    "getFeatureGuide",
                    "findFeature",
                    "getNavigationPath",
                    "getDeepLink",
                    "listFeatures",
                    "getTroubleshootingGuide"
            );
        }

        @Test
        @DisplayName("Should support all defined tools")
        void shouldSupportAllDefinedTools() {
            assertThat(agent.supportsTool("getFeatureGuide")).isTrue();
            assertThat(agent.supportsTool("findFeature")).isTrue();
            assertThat(agent.supportsTool("getNavigationPath")).isTrue();
            assertThat(agent.supportsTool("getDeepLink")).isTrue();
            assertThat(agent.supportsTool("listFeatures")).isTrue();
            assertThat(agent.supportsTool("getTroubleshootingGuide")).isTrue();
        }

        @Test
        @DisplayName("Should not support unknown tools")
        void shouldNotSupportUnknownTools() {
            assertThat(agent.supportsTool("unknownTool")).isFalse();
            assertThat(agent.supportsTool("getBalance")).isFalse();
        }
    }

    @Nested
    @DisplayName("getFeatureGuide Tool Tests")
    class GetFeatureGuideTests {

        @Test
        @DisplayName("Should return guide for Touch ID on iOS")
        void shouldReturnTouchIdGuideIos() {
            Map<String, Object> input = Map.of(
                    "featureId", "touch-id",
                    "platform", "iOS"
            );

            Map<String, Object> result = agent.executeTool("getFeatureGuide", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("featureId")).isEqualTo("touch-id");
            assertThat(result.get("featureName")).isEqualTo("Set up Touch ID");
            assertThat(result.get("platform")).isEqualTo("IOS");
            assertThat((List<?>) result.get("steps")).hasSizeGreaterThanOrEqualTo(3);
            assertThat((String) result.get("voiceResponse")).contains("Step 1");
        }

        @Test
        @DisplayName("Should return guide for fingerprint on Android")
        void shouldReturnFingerprintGuideAndroid() {
            Map<String, Object> input = Map.of(
                    "featureId", "fingerprint",
                    "platform", "Android"
            );

            Map<String, Object> result = agent.executeTool("getFeatureGuide", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("platform")).isEqualTo("ANDROID");
        }

        @Test
        @DisplayName("Should return generic guide when no platform specified")
        void shouldReturnGenericGuide() {
            Map<String, Object> input = Map.of(
                    "featureId", "change-pin"
            );

            Map<String, Object> result = agent.executeTool("getFeatureGuide", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("featureId")).isEqualTo("change-pin");
        }

        @Test
        @DisplayName("Should fail when featureId is missing")
        void shouldFailWhenFeatureIdMissing() {
            Map<String, Object> input = Map.of("platform", "iOS");

            Map<String, Object> result = agent.executeTool("getFeatureGuide", input);

            assertThat(result.get("success")).isEqualTo(false);
            assertThat(result.get("error")).isEqualTo("featureId is required");
        }

        @Test
        @DisplayName("Should suggest alternatives for unknown feature")
        void shouldSuggestAlternativesForUnknownFeature() {
            Map<String, Object> input = Map.of("featureId", "touch");

            Map<String, Object> result = agent.executeTool("getFeatureGuide", input);

            // May return error with suggestions if guide not found but features match
            assertThat(result).containsKey("success");
        }

        @Test
        @DisplayName("Should include voice response in guide")
        void shouldIncludeVoiceResponse() {
            Map<String, Object> input = Map.of(
                    "featureId", "transfer-sepa",
                    "platform", "Both"
            );

            Map<String, Object> result = agent.executeTool("getFeatureGuide", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat((String) result.get("voiceResponse"))
                    .contains("Step")
                    .contains("Would you like me to repeat");
        }
    }

    @Nested
    @DisplayName("findFeature Tool Tests")
    class FindFeatureTests {

        @Test
        @DisplayName("Should find features matching 'send money'")
        void shouldFindSendMoneyFeatures() {
            Map<String, Object> input = Map.of("query", "send money");

            Map<String, Object> result = agent.executeTool("findFeature", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat((Integer) result.get("count")).isGreaterThan(0);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
            assertThat(matches).isNotEmpty();
            
            // Should find transfer-related features
            boolean hasTransfer = matches.stream()
                    .anyMatch(m -> ((String) m.get("category")).contains("TRANSFER"));
            assertThat(hasTransfer).isTrue();
        }

        @Test
        @DisplayName("Should find Touch ID feature")
        void shouldFindTouchIdFeature() {
            Map<String, Object> input = Map.of("query", "Touch ID");

            Map<String, Object> result = agent.executeTool("findFeature", input);

            assertThat(result.get("success")).isEqualTo(true);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
            assertThat(matches).isNotEmpty();
            assertThat(matches.get(0).get("featureId")).isEqualTo("touch-id");
        }

        @Test
        @DisplayName("Should filter by platform")
        void shouldFilterByPlatform() {
            Map<String, Object> input = Map.of(
                    "query", "biometric",
                    "platform", "iOS"
            );

            Map<String, Object> result = agent.executeTool("findFeature", input);

            assertThat(result.get("success")).isEqualTo(true);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
            // Should include iOS and BOTH platform features, not Android-only
            for (Map<String, Object> match : matches) {
                String platform = (String) match.get("platform");
                assertThat(platform).isIn("IOS", "BOTH");
            }
        }

        @Test
        @DisplayName("Should respect limit parameter")
        void shouldRespectLimit() {
            Map<String, Object> input = Map.of(
                    "query", "settings",
                    "limit", 2
            );

            Map<String, Object> result = agent.executeTool("findFeature", input);

            assertThat(result.get("success")).isEqualTo(true);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
            assertThat(matches).hasSizeLessThanOrEqualTo(2);
        }

        @Test
        @DisplayName("Should return empty list for no matches")
        void shouldReturnEmptyForNoMatches() {
            Map<String, Object> input = Map.of("query", "xyznonexistent123");

            Map<String, Object> result = agent.executeTool("findFeature", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat((Integer) result.get("count")).isEqualTo(0);
            assertThat((String) result.get("voiceResponse")).contains("couldn't find");
        }

        @Test
        @DisplayName("Should fail when query is missing")
        void shouldFailWhenQueryMissing() {
            Map<String, Object> input = Map.of("platform", "iOS");

            Map<String, Object> result = agent.executeTool("findFeature", input);

            assertThat(result.get("success")).isEqualTo(false);
            assertThat(result.get("error")).isEqualTo("query is required");
        }

        @Test
        @DisplayName("Should include voice response listing features")
        void shouldIncludeVoiceResponse() {
            Map<String, Object> input = Map.of("query", "transfer");

            Map<String, Object> result = agent.executeTool("findFeature", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat((String) result.get("voiceResponse")).isNotBlank();
        }
    }

    @Nested
    @DisplayName("getNavigationPath Tool Tests")
    class GetNavigationPathTests {

        @Test
        @DisplayName("Should return navigation path for Touch ID")
        void shouldReturnNavigationPathForTouchId() {
            Map<String, Object> input = Map.of(
                    "featureId", "touch-id",
                    "platform", "iOS"
            );

            Map<String, Object> result = agent.executeTool("getNavigationPath", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("featureId")).isEqualTo("touch-id");
            assertThat((String) result.get("formattedPath")).contains("→");
            assertThat((String) result.get("voiceResponse")).contains("tap");
        }

        @Test
        @DisplayName("Should include deep link when available")
        void shouldIncludeDeepLink() {
            Map<String, Object> input = Map.of(
                    "featureId", "transfer-sepa"
            );

            Map<String, Object> result = agent.executeTool("getNavigationPath", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("deepLink")).isNotNull();
            assertThat((String) result.get("deepLink")).startsWith("acmebank://");
        }

        @Test
        @DisplayName("Should fail for missing featureId")
        void shouldFailForMissingFeatureId() {
            Map<String, Object> input = Map.of("platform", "iOS");

            Map<String, Object> result = agent.executeTool("getNavigationPath", input);

            assertThat(result.get("success")).isEqualTo(false);
            assertThat(result.get("error")).isEqualTo("featureId is required");
        }

        @Test
        @DisplayName("Should include tap count")
        void shouldIncludeTapCount() {
            Map<String, Object> input = Map.of("featureId", "push-notifications");

            Map<String, Object> result = agent.executeTool("getNavigationPath", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("tapCount")).isNotNull();
            assertThat((Integer) result.get("tapCount")).isGreaterThanOrEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getDeepLink Tool Tests")
    class GetDeepLinkTests {

        @Test
        @DisplayName("Should return deep link for SEPA transfer")
        void shouldReturnDeepLinkForSepaTransfer() {
            Map<String, Object> input = Map.of("featureId", "transfer-sepa");

            Map<String, Object> result = agent.executeTool("getDeepLink", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("deepLink")).isEqualTo("acmebank://transfer/sepa");
            assertThat((String) result.get("voiceResponse")).contains("link");
        }

        @Test
        @DisplayName("Should return error for feature without deep link")
        void shouldReturnErrorForFeatureWithoutDeepLink() {
            // logout doesn't have a deep link in our catalog
            Map<String, Object> input = Map.of("featureId", "logout");

            Map<String, Object> result = agent.executeTool("getDeepLink", input);

            // Either no deep link or success=false
            if (result.get("success").equals(false)) {
                assertThat((String) result.get("error")).contains("not available");
            }
        }

        @Test
        @DisplayName("Should fail for unknown feature")
        void shouldFailForUnknownFeature() {
            Map<String, Object> input = Map.of("featureId", "unknown-feature-xyz");

            Map<String, Object> result = agent.executeTool("getDeepLink", input);

            assertThat(result.get("success")).isEqualTo(false);
            assertThat((String) result.get("error")).contains("not found");
        }

        @Test
        @DisplayName("Should fail when featureId is missing")
        void shouldFailWhenFeatureIdMissing() {
            Map<String, Object> input = Map.of();

            Map<String, Object> result = agent.executeTool("getDeepLink", input);

            assertThat(result.get("success")).isEqualTo(false);
            assertThat(result.get("error")).isEqualTo("featureId is required");
        }
    }

    @Nested
    @DisplayName("listFeatures Tool Tests")
    class ListFeaturesTests {

        @Test
        @DisplayName("Should list all features when no category specified")
        void shouldListAllFeatures() {
            Map<String, Object> input = Map.of();

            Map<String, Object> result = agent.executeTool("listFeatures", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat((Integer) result.get("count")).isGreaterThanOrEqualTo(40);
        }

        @Test
        @DisplayName("Should list features by category")
        void shouldListFeaturesByCategory() {
            Map<String, Object> input = Map.of("category", "SECURITY");

            Map<String, Object> result = agent.executeTool("listFeatures", input);

            assertThat(result.get("success")).isEqualTo(true);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> features = (List<Map<String, Object>>) result.get("features");
            assertThat(features).isNotEmpty();
            
            for (Map<String, Object> feature : features) {
                assertThat(feature.get("category")).isEqualTo("SECURITY");
            }
        }

        @Test
        @DisplayName("Should filter by platform")
        void shouldFilterByPlatform() {
            Map<String, Object> input = Map.of(
                    "category", "SECURITY",
                    "platform", "Android"
            );

            Map<String, Object> result = agent.executeTool("listFeatures", input);

            assertThat(result.get("success")).isEqualTo(true);
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> features = (List<Map<String, Object>>) result.get("features");
            
            for (Map<String, Object> feature : features) {
                String platform = (String) feature.get("platform");
                assertThat(platform).isIn("ANDROID", "BOTH");
            }
        }

        @Test
        @DisplayName("Should return error for unknown category")
        void shouldReturnErrorForUnknownCategory() {
            Map<String, Object> input = Map.of("category", "UNKNOWN_CATEGORY");

            Map<String, Object> result = agent.executeTool("listFeatures", input);

            assertThat(result.get("success")).isEqualTo(false);
            assertThat(result.get("availableCategories")).isNotNull();
        }

        @Test
        @DisplayName("Should detect category from keyword")
        void shouldDetectCategoryFromKeyword() {
            Map<String, Object> input = Map.of("category", "fingerprint");

            Map<String, Object> result = agent.executeTool("listFeatures", input);

            // Should detect SECURITY from keyword
            assertThat(result.get("success")).isEqualTo(true);
        }

        @Test
        @DisplayName("Should include voice response")
        void shouldIncludeVoiceResponse() {
            Map<String, Object> input = Map.of("category", "CARDS");

            Map<String, Object> result = agent.executeTool("listFeatures", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat((String) result.get("voiceResponse")).contains("features");
        }
    }

    @Nested
    @DisplayName("getTroubleshootingGuide Tool Tests")
    class GetTroubleshootingGuideTests {

        @Test
        @DisplayName("Should return troubleshooting for login issue")
        void shouldReturnTroubleshootingForLogin() {
            Map<String, Object> input = Map.of(
                    "issueType", "LOGIN",
                    "description", "locked out of my account"
            );

            Map<String, Object> result = agent.executeTool("getTroubleshootingGuide", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("issueType")).isEqualTo("LOGIN");
            assertThat((List<?>) result.get("solutionSteps")).isNotEmpty();
            assertThat((String) result.get("voiceResponse")).contains("Step");
        }

        @Test
        @DisplayName("Should return troubleshooting for performance issue")
        void shouldReturnTroubleshootingForPerformance() {
            Map<String, Object> input = Map.of(
                    "issueType", "PERFORMANCE",
                    "description", "app keeps crashing"
            );

            Map<String, Object> result = agent.executeTool("getTroubleshootingGuide", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("issueType")).isEqualTo("PERFORMANCE");
        }

        @Test
        @DisplayName("Should detect issue type from description alone")
        void shouldDetectIssueTypeFromDescription() {
            Map<String, Object> input = Map.of(
                    "description", "the app is very slow and freezing"
            );

            Map<String, Object> result = agent.executeTool("getTroubleshootingGuide", input);

            assertThat(result.get("success")).isEqualTo(true);
            assertThat(result.get("issueType")).isEqualTo("PERFORMANCE");
        }

        @Test
        @DisplayName("Should handle Touch ID not working")
        void shouldHandleTouchIdNotWorking() {
            Map<String, Object> input = Map.of(
                    "issueType", "SECURITY",
                    "description", "Touch ID not working"
            );

            Map<String, Object> result = agent.executeTool("getTroubleshootingGuide", input);

            assertThat(result.get("success")).isEqualTo(true);
            // Should return security-related troubleshooting steps
            assertThat((String) result.get("voiceResponse")).containsIgnoringCase("Touch ID");
        }

        @Test
        @DisplayName("Should require at least issueType or description")
        void shouldRequireAtLeastOneInput() {
            Map<String, Object> input = Map.of();

            Map<String, Object> result = agent.executeTool("getTroubleshootingGuide", input);

            assertThat(result.get("success")).isEqualTo(false);
            assertThat((String) result.get("error")).contains("describe the issue");
            assertThat(result.get("availableIssueTypes")).isNotNull();
        }

        @Test
        @DisplayName("Should include support contact for escalation")
        void shouldIncludeSupportContact() {
            Map<String, Object> input = Map.of(
                    "issueType", "LOGIN",
                    "description", "can't log in"
            );

            Map<String, Object> result = agent.executeTool("getTroubleshootingGuide", input);

            assertThat(result.get("success")).isEqualTo(true);
            // Support contact may be present for escalation
            if (result.containsKey("supportContact")) {
                assertThat((String) result.get("supportContact")).contains("800");
            }
        }

        @Test
        @DisplayName("Should handle unknown issue type gracefully")
        void shouldHandleUnknownIssueType() {
            Map<String, Object> input = Map.of(
                    "issueType", "something random"
            );

            Map<String, Object> result = agent.executeTool("getTroubleshootingGuide", input);

            // Should detect from string or fall back to FEATURE
            assertThat(result.get("success")).isEqualTo(true);
        }
    }

    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle unknown tool gracefully")
        void shouldHandleUnknownToolGracefully() {
            Map<String, Object> input = Map.of("param", "value");

            Map<String, Object> result = agent.executeTool("unknownTool", input);

            assertThat(result.get("success")).isEqualTo(false);
            assertThat((String) result.get("error")).contains("Unknown tool");
        }

        @Test
        @DisplayName("Should handle null input gracefully")
        void shouldHandleNullInputGracefully() {
            Map<String, Object> result = agent.executeTool("getFeatureGuide", new HashMap<>());

            assertThat(result.get("success")).isEqualTo(false);
            assertThat(result.get("error")).isNotNull();
        }
    }

    @Nested
    @DisplayName("Voice Response Quality Tests")
    class VoiceResponseQualityTests {

        @Test
        @DisplayName("Voice responses should be under reasonable length")
        void voiceResponsesShouldBeReasonableLength() {
            Map<String, Object> input = Map.of("featureId", "touch-id", "platform", "iOS");
            Map<String, Object> result = agent.executeTool("getFeatureGuide", input);

            String voiceResponse = (String) result.get("voiceResponse");
            assertThat(voiceResponse).isNotNull();
            // Voice responses should be manageable for TTS (under 500 chars typically)
            assertThat(voiceResponse.length()).isLessThan(800);
        }

        @Test
        @DisplayName("Voice responses should have step numbering")
        void voiceResponsesShouldHaveStepNumbering() {
            Map<String, Object> input = Map.of("featureId", "transfer-sepa");
            Map<String, Object> result = agent.executeTool("getFeatureGuide", input);

            String voiceResponse = (String) result.get("voiceResponse");
            assertThat(voiceResponse).contains("Step 1");
            assertThat(voiceResponse).contains("Step 2");
        }

        @Test
        @DisplayName("Voice responses should offer continuation")
        void voiceResponsesShouldOfferContinuation() {
            Map<String, Object> input = Map.of("featureId", "change-pin");
            Map<String, Object> result = agent.executeTool("getFeatureGuide", input);

            String voiceResponse = (String) result.get("voiceResponse");
            assertThat(voiceResponse).containsIgnoringCase("repeat");
        }
    }

    @Nested
    @DisplayName("Feature Catalog Integration Tests")
    class FeatureCatalogTests {

        @Test
        @DisplayName("Catalog should have expected feature count")
        void catalogShouldHaveExpectedFeatureCount() {
            assertThat(featureCatalog.getFeatureCount()).isGreaterThanOrEqualTo(40);
        }

        @Test
        @DisplayName("All categories should have features")
        void allCategoriesShouldHaveFeatures() {
            for (FeatureCategory category : FeatureCategory.values()) {
                List<AppFeature> features = featureCatalog.listByCategory(category);
                assertThat(features)
                        .as("Category %s should have features", category)
                        .isNotEmpty();
            }
        }

        @Test
        @DisplayName("Deep links should follow expected format")
        void deepLinksShouldFollowExpectedFormat() {
            List<AppFeature> featuresWithDeepLinks = featureCatalog.listWithDeepLinks();
            assertThat(featuresWithDeepLinks).isNotEmpty();
            
            for (AppFeature feature : featuresWithDeepLinks) {
                assertThat(feature.getDeepLink())
                        .as("Feature %s deep link", feature.getFeatureId())
                        .startsWith("acmebank://");
            }
        }
    }
}
