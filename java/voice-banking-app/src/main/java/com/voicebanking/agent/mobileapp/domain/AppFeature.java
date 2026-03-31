package com.voicebanking.agent.mobileapp.domain;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a feature in the Acme Bank mobile app.
 * 
 * Contains metadata about the feature including name, category,
 * platform availability, and deep link information.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class AppFeature {
    private final String featureId;
    private final String nameEn;
    private final String nameDe;
    private final FeatureCategory category;
    private final String description;
    private final String minAppVersion;
    private final Platform platformAvailability;
    private final List<String> prerequisites;
    private final String deepLink;
    private final List<String> keywords;

    private AppFeature(Builder builder) {
        this.featureId = builder.featureId;
        this.nameEn = builder.nameEn;
        this.nameDe = builder.nameDe;
        this.category = builder.category;
        this.description = builder.description;
        this.minAppVersion = builder.minAppVersion;
        this.platformAvailability = builder.platformAvailability;
        this.prerequisites = builder.prerequisites != null ? List.copyOf(builder.prerequisites) : List.of();
        this.deepLink = builder.deepLink;
        this.keywords = builder.keywords != null ? List.copyOf(builder.keywords) : List.of();
    }

    // Getters
    public String getFeatureId() { return featureId; }
    public String getNameEn() { return nameEn; }
    public String getNameDe() { return nameDe; }
    public FeatureCategory getCategory() { return category; }
    public String getDescription() { return description; }
    public String getMinAppVersion() { return minAppVersion; }
    public Platform getPlatformAvailability() { return platformAvailability; }
    public List<String> getPrerequisites() { return prerequisites; }
    public String getDeepLink() { return deepLink; }
    public List<String> getKeywords() { return keywords; }

    /**
     * Check if feature is available on the given platform.
     */
    public boolean isAvailableOn(Platform platform) {
        if (platformAvailability == Platform.BOTH) {
            return true;
        }
        return platformAvailability == platform;
    }

    /**
     * Check if feature matches the search query.
     */
    public boolean matchesQuery(String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        String lower = query.toLowerCase();
        
        if (nameEn.toLowerCase().contains(lower) || nameDe.toLowerCase().contains(lower)) {
            return true;
        }
        if (description != null && description.toLowerCase().contains(lower)) {
            return true;
        }
        for (String keyword : keywords) {
            if (keyword.toLowerCase().contains(lower) || lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the display name based on language preference.
     */
    public String getDisplayName(String language) {
        if ("de".equalsIgnoreCase(language)) {
            return nameDe != null ? nameDe : nameEn;
        }
        return nameEn;
    }

    /**
     * Convert to Map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("featureId", featureId);
        map.put("name", nameEn);
        map.put("nameDe", nameDe);
        map.put("category", category.name());
        map.put("description", description);
        map.put("minAppVersion", minAppVersion);
        map.put("platform", platformAvailability.name());
        map.put("prerequisites", prerequisites);
        if (deepLink != null) {
            map.put("deepLink", deepLink);
        }
        return map;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String featureId;
        private String nameEn;
        private String nameDe;
        private FeatureCategory category;
        private String description;
        private String minAppVersion = "1.0.0";
        private Platform platformAvailability = Platform.BOTH;
        private List<String> prerequisites;
        private String deepLink;
        private List<String> keywords;

        public Builder featureId(String featureId) { this.featureId = featureId; return this; }
        public Builder nameEn(String nameEn) { this.nameEn = nameEn; return this; }
        public Builder nameDe(String nameDe) { this.nameDe = nameDe; return this; }
        public Builder category(FeatureCategory category) { this.category = category; return this; }
        public Builder description(String description) { this.description = description; return this; }
        public Builder minAppVersion(String minAppVersion) { this.minAppVersion = minAppVersion; return this; }
        public Builder platformAvailability(Platform platformAvailability) { this.platformAvailability = platformAvailability; return this; }
        public Builder prerequisites(List<String> prerequisites) { this.prerequisites = prerequisites; return this; }
        public Builder deepLink(String deepLink) { this.deepLink = deepLink; return this; }
        public Builder keywords(List<String> keywords) { this.keywords = keywords; return this; }

        public AppFeature build() {
            if (featureId == null || featureId.isBlank()) {
                throw new IllegalStateException("featureId is required");
            }
            if (nameEn == null || nameEn.isBlank()) {
                throw new IllegalStateException("nameEn is required");
            }
            if (category == null) {
                throw new IllegalStateException("category is required");
            }
            return new AppFeature(this);
        }
    }
}
