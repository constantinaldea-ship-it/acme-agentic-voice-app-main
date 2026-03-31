package com.voicebanking.agent.mobileapp;

import com.voicebanking.agent.Agent;
import com.voicebanking.agent.mobileapp.catalog.AppFeatureCatalog;
import com.voicebanking.agent.mobileapp.domain.*;
import com.voicebanking.agent.mobileapp.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * MobileAppAssistanceAgent
 * 
 * Provides step-by-step guidance for using the Acme Bank Mobile Banking app.
 * Handles "How do I...?" queries for app navigation, feature discovery,
 * troubleshooting, and settings configuration.
 * 
 * <h2>Tools Provided</h2>
 * <ul>
 *   <li><b>getFeatureGuide</b> - Step-by-step guide for a feature</li>
 *   <li><b>findFeature</b> - Search features by query</li>
 *   <li><b>getNavigationPath</b> - Path to reach a feature in the app</li>
 *   <li><b>getDeepLink</b> - Deep link URL for direct navigation</li>
 *   <li><b>listFeatures</b> - List features by category</li>
 *   <li><b>getTroubleshootingGuide</b> - Troubleshoot common issues</li>
 * </ul>
 * 
 * <h2>Category</h2>
 * Category 1: Mobile App AI Assistance
 * 
 * @author Augment Agent
 * @since 2026-01-25
 * @see Agent
 */
@Component
public class MobileAppAssistanceAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(MobileAppAssistanceAgent.class);

    private static final String AGENT_ID = "mobile-app-assistance";
    private static final List<String> TOOL_IDS = List.of(
            "getFeatureGuide",
            "findFeature",
            "getNavigationPath",
            "getDeepLink",
            "listFeatures",
            "getTroubleshootingGuide"
    );

    private final AppFeatureCatalog featureCatalog;
    private final FeatureGuideService guideService;
    private final NavigationService navigationService;
    private final TroubleshootingService troubleshootingService;

    public MobileAppAssistanceAgent(
            AppFeatureCatalog featureCatalog,
            FeatureGuideService guideService,
            NavigationService navigationService,
            TroubleshootingService troubleshootingService) {
        this.featureCatalog = featureCatalog;
        this.guideService = guideService;
        this.navigationService = navigationService;
        this.troubleshootingService = troubleshootingService;
        
        log.info("MobileAppAssistanceAgent initialized with {} features in catalog", 
                featureCatalog.getFeatureCount());
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    @Override
    public String getDescription() {
        return "Provides step-by-step guidance for using the Acme Bank Mobile Banking app, " +
               "including feature navigation, settings configuration, and troubleshooting";
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
                case "getFeatureGuide" -> getFeatureGuide(input);
                case "findFeature" -> findFeature(input);
                case "getNavigationPath" -> getNavigationPath(input);
                case "getDeepLink" -> getDeepLink(input);
                case "listFeatures" -> listFeatures(input);
                case "getTroubleshootingGuide" -> getTroubleshootingGuide(input);
                default -> Map.of("error", "Unknown tool: " + toolId, "success", false);
            };
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolId, e.getMessage(), e);
            return Map.of("error", e.getMessage(), "success", false);
        }
    }

    /**
     * Get step-by-step guide for a feature.
     * 
     * Input:
     * - featureId (required): Feature identifier (e.g., "touch-id")
     * - platform (optional): "iOS", "Android", or "Both" (default: "Both")
     * 
     * Output:
     * - success: boolean
     * - guide: StepGuide map
     * - voiceResponse: Voice-formatted response
     */
    private Map<String, Object> getFeatureGuide(Map<String, Object> input) {
        String featureId = (String) input.get("featureId");
        if (featureId == null || featureId.isBlank()) {
            return Map.of("success", false, "error", "featureId is required");
        }

        String platformStr = (String) input.getOrDefault("platform", "Both");
        Platform platform = Platform.fromUserInput(platformStr);

        Optional<StepGuide> guideOpt = guideService.getGuide(featureId, platform);
        
        if (guideOpt.isEmpty()) {
            // Try to find a similar feature
            List<AppFeature> matches = featureCatalog.search(featureId);
            if (!matches.isEmpty()) {
                return Map.of(
                        "success", false,
                        "error", "Guide not found for: " + featureId,
                        "suggestions", matches.stream()
                                .limit(3)
                                .map(AppFeature::toMap)
                                .collect(Collectors.toList()),
                        "voiceResponse", "I couldn't find a guide for that specific feature. Did you mean " +
                                matches.get(0).getNameEn() + "?"
                );
            }
            return Map.of("success", false, "error", "Feature not found: " + featureId);
        }

        StepGuide guide = guideOpt.get();
        Map<String, Object> result = new HashMap<>(guide.toMap());
        result.put("success", true);
        return result;
    }

    /**
     * Search for features matching a query.
     * 
     * Input:
     * - query (required): Search query (e.g., "send money", "Touch ID")
     * - platform (optional): Filter by platform
     * - limit (optional): Maximum results (default: 5)
     * 
     * Output:
     * - success: boolean
     * - matches: List of matching features
     * - voiceResponse: Voice-formatted response
     */
    private Map<String, Object> findFeature(Map<String, Object> input) {
        String query = (String) input.get("query");
        if (query == null || query.isBlank()) {
            return Map.of("success", false, "error", "query is required");
        }

        String platformStr = (String) input.get("platform");
        Platform platform = platformStr != null ? Platform.fromUserInput(platformStr) : null;
        int limit = input.containsKey("limit") ? ((Number) input.get("limit")).intValue() : 5;

        List<AppFeature> matches;
        if (platform != null && platform != Platform.BOTH) {
            matches = featureCatalog.search(query, platform);
        } else {
            matches = featureCatalog.search(query);
        }

        List<AppFeature> limited = matches.stream().limit(limit).collect(Collectors.toList());

        if (limited.isEmpty()) {
            return Map.of(
                    "success", true,
                    "matches", List.of(),
                    "count", 0,
                    "voiceResponse", "I couldn't find any features matching '" + query + 
                            "'. Try describing what you want to do."
            );
        }

        String voiceResponse;
        if (limited.size() == 1) {
            voiceResponse = "I found " + limited.get(0).getNameEn() + ". Would you like me to show you how to use it?";
        } else {
            voiceResponse = "I found " + limited.size() + " features: " +
                    limited.stream()
                            .map(AppFeature::getNameEn)
                            .collect(Collectors.joining(", ")) +
                    ". Which one would you like help with?";
        }

        return Map.of(
                "success", true,
                "matches", limited.stream().map(AppFeature::toMap).collect(Collectors.toList()),
                "count", limited.size(),
                "totalMatches", matches.size(),
                "voiceResponse", voiceResponse
        );
    }

    /**
     * Get navigation path to reach a feature in the app.
     * 
     * Input:
     * - featureId (required): Feature identifier
     * - platform (optional): Platform for platform-specific paths
     * 
     * Output:
     * - success: boolean
     * - path: NavigationPath map
     * - voiceResponse: Voice-formatted navigation instructions
     */
    private Map<String, Object> getNavigationPath(Map<String, Object> input) {
        String featureId = (String) input.get("featureId");
        if (featureId == null || featureId.isBlank()) {
            return Map.of("success", false, "error", "featureId is required");
        }

        String platformStr = (String) input.getOrDefault("platform", "Both");
        Platform platform = Platform.fromUserInput(platformStr);

        Optional<NavigationPath> pathOpt = navigationService.getNavigationPath(featureId, platform);
        
        if (pathOpt.isEmpty()) {
            return Map.of("success", false, "error", "Navigation path not found for: " + featureId);
        }

        NavigationPath path = pathOpt.get();
        Map<String, Object> result = new HashMap<>(path.toMap());
        result.put("success", true);
        return result;
    }

    /**
     * Get deep link URL for direct navigation to a feature.
     * 
     * Input:
     * - featureId (required): Feature identifier
     * 
     * Output:
     * - success: boolean
     * - deepLink: The deep link URL
     * - voiceResponse: Voice response offering to send the link
     */
    private Map<String, Object> getDeepLink(Map<String, Object> input) {
        String featureId = (String) input.get("featureId");
        if (featureId == null || featureId.isBlank()) {
            return Map.of("success", false, "error", "featureId is required");
        }

        Optional<String> deepLinkOpt = navigationService.getDeepLink(featureId);
        
        if (deepLinkOpt.isEmpty()) {
            // Check if feature exists but has no deep link
            Optional<AppFeature> featureOpt = featureCatalog.getFeature(featureId);
            if (featureOpt.isPresent()) {
                return Map.of(
                        "success", false,
                        "error", "Deep link not available for this feature",
                        "featureId", featureId,
                        "featureName", featureOpt.get().getNameEn(),
                        "voiceResponse", "Deep links aren't available for " + featureOpt.get().getNameEn() + 
                                ". Let me show you how to navigate there instead."
                );
            }
            return Map.of("success", false, "error", "Feature not found: " + featureId);
        }

        String deepLink = deepLinkOpt.get();
        Optional<AppFeature> featureOpt = featureCatalog.getFeature(featureId);
        String featureName = featureOpt.map(AppFeature::getNameEn).orElse(featureId);

        return Map.of(
                "success", true,
                "featureId", featureId,
                "featureName", featureName,
                "deepLink", deepLink,
                "voiceResponse", "I can send you a link that will take you directly to " + featureName + 
                        ". Would you like me to do that?"
        );
    }

    /**
     * List features by category.
     * 
     * Input:
     * - category (optional): Category name (SECURITY, TRANSFERS, ACCOUNTS, CARDS, SETTINGS, SUPPORT)
     * - platform (optional): Filter by platform
     * 
     * Output:
     * - success: boolean
     * - features: List of features
     * - voiceResponse: Voice-formatted list
     */
    private Map<String, Object> listFeatures(Map<String, Object> input) {
        String categoryStr = (String) input.get("category");
        String platformStr = (String) input.get("platform");

        List<AppFeature> features;

        if (categoryStr != null && !categoryStr.isBlank()) {
            try {
                FeatureCategory category = FeatureCategory.valueOf(categoryStr.toUpperCase());
                features = featureCatalog.listByCategory(category);
            } catch (IllegalArgumentException e) {
                // Try to find category from query
                FeatureCategory detected = FeatureCategory.fromQuery(categoryStr);
                if (detected != null) {
                    features = featureCatalog.listByCategory(detected);
                } else {
                    return Map.of(
                            "success", false,
                            "error", "Unknown category: " + categoryStr,
                            "availableCategories", Arrays.stream(FeatureCategory.values())
                                    .map(FeatureCategory::name)
                                    .collect(Collectors.toList())
                    );
                }
            }
        } else {
            features = featureCatalog.listAll();
        }

        // Filter by platform if specified
        if (platformStr != null && !platformStr.isBlank()) {
            Platform platform = Platform.fromUserInput(platformStr);
            if (platform != Platform.BOTH) {
                features = features.stream()
                        .filter(f -> f.isAvailableOn(platform))
                        .collect(Collectors.toList());
            }
        }

        String voiceResponse;
        if (categoryStr != null) {
            voiceResponse = "Here are " + features.size() + " features in " + categoryStr + ": " +
                    features.stream()
                            .limit(5)
                            .map(AppFeature::getNameEn)
                            .collect(Collectors.joining(", "));
            if (features.size() > 5) {
                voiceResponse += ", and " + (features.size() - 5) + " more";
            }
        } else {
            voiceResponse = "We have " + features.size() + " features across categories like Security, " +
                    "Transfers, Accounts, Cards, Settings, and Support. Which category interests you?";
        }

        return Map.of(
                "success", true,
                "features", features.stream().map(AppFeature::toMap).collect(Collectors.toList()),
                "count", features.size(),
                "voiceResponse", voiceResponse
        );
    }

    /**
     * Get troubleshooting guide for an issue.
     * 
     * Input:
     * - issueType (optional): Issue type (LOGIN, PERFORMANCE, FEATURE, SECURITY, UPDATE, NETWORK, NOTIFICATION)
     * - description (optional): Issue description for better matching
     * 
     * Output:
     * - success: boolean
     * - solution: TroubleshootingSolution map
     * - voiceResponse: Voice-formatted troubleshooting steps
     */
    private Map<String, Object> getTroubleshootingGuide(Map<String, Object> input) {
        String issueTypeStr = (String) input.get("issueType");
        String description = (String) input.get("description");

        TroubleshootingSolution solution;

        if (issueTypeStr != null && !issueTypeStr.isBlank()) {
            try {
                IssueType issueType = IssueType.valueOf(issueTypeStr.toUpperCase());
                solution = troubleshootingService.getTroubleshootingSolution(issueType, description);
            } catch (IllegalArgumentException e) {
                // Try to detect from the type string as description
                solution = troubleshootingService.getTroubleshootingSolution(issueTypeStr);
            }
        } else if (description != null && !description.isBlank()) {
            solution = troubleshootingService.getTroubleshootingSolution(description);
        } else {
            return Map.of(
                    "success", false,
                    "error", "Please describe the issue you're experiencing",
                    "availableIssueTypes", Arrays.stream(IssueType.values())
                            .map(IssueType::name)
                            .collect(Collectors.toList()),
                    "voiceResponse", "What issue are you experiencing? For example, login problems, " +
                            "app crashing, or a feature not working?"
            );
        }

        Map<String, Object> result = new HashMap<>(solution.toMap());
        result.put("success", true);
        return result;
    }
}
