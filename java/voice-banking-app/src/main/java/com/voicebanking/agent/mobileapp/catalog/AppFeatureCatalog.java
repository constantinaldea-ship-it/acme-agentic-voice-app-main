package com.voicebanking.agent.mobileapp.catalog;

import com.voicebanking.agent.mobileapp.domain.AppFeature;
import com.voicebanking.agent.mobileapp.domain.FeatureCategory;
import com.voicebanking.agent.mobileapp.domain.Platform;

import java.util.List;
import java.util.Optional;

/**
 * Interface for accessing the mobile app feature catalog.
 * 
 * Provides methods to retrieve, search, and filter app features
 * for the MobileAppAssistanceAgent.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public interface AppFeatureCatalog {

    /**
     * Get a feature by its unique identifier.
     * 
     * @param featureId the feature identifier (e.g., "touch-id", "transfer-sepa")
     * @return the feature if found
     */
    Optional<AppFeature> getFeature(String featureId);

    /**
     * List all features in a category.
     * 
     * @param category the feature category
     * @return list of features in that category
     */
    List<AppFeature> listByCategory(FeatureCategory category);

    /**
     * List all features available on a specific platform.
     * 
     * @param platform the target platform
     * @return list of features available on that platform
     */
    List<AppFeature> listByPlatform(Platform platform);

    /**
     * Search features by query string.
     * Matches against feature name, description, and keywords.
     * 
     * @param query the search query
     * @return list of matching features, ordered by relevance
     */
    List<AppFeature> search(String query);

    /**
     * Search features by query, filtered by platform.
     * 
     * @param query the search query
     * @param platform the target platform
     * @return list of matching features available on the platform
     */
    List<AppFeature> search(String query, Platform platform);

    /**
     * Get all features in the catalog.
     * 
     * @return list of all features
     */
    List<AppFeature> listAll();

    /**
     * Get all features with deep links available.
     * 
     * @return list of features with deep link support
     */
    List<AppFeature> listWithDeepLinks();

    /**
     * Get the total number of features in the catalog.
     * 
     * @return feature count
     */
    int getFeatureCount();
}
