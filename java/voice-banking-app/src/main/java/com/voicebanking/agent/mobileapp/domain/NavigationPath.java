package com.voicebanking.agent.mobileapp.domain;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Navigation path to reach a feature in the mobile app.
 * 
 * Represents the menu/screen hierarchy to navigate to a specific feature.
 * Used by getNavigationPath tool to show users how to find features.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class NavigationPath {
    private final String featureId;
    private final Platform platform;
    private final List<String> pathSegments;
    private final String startingPoint;
    private final String deepLink;

    private NavigationPath(Builder builder) {
        this.featureId = builder.featureId;
        this.platform = builder.platform;
        this.pathSegments = builder.pathSegments != null ? List.copyOf(builder.pathSegments) : List.of();
        this.startingPoint = builder.startingPoint;
        this.deepLink = builder.deepLink;
    }

    // Getters
    public String getFeatureId() { return featureId; }
    public Platform getPlatform() { return platform; }
    public List<String> getPathSegments() { return pathSegments; }
    public String getStartingPoint() { return startingPoint; }
    public String getDeepLink() { return deepLink; }

    /**
     * Get the full navigation path as a formatted string.
     * Example: "Home → Menu → Settings → Security → Touch ID"
     */
    public String getFormattedPath() {
        List<String> fullPath = new ArrayList<>();
        if (startingPoint != null) {
            fullPath.add(startingPoint);
        }
        fullPath.addAll(pathSegments);
        return String.join(" → ", fullPath);
    }

    /**
     * Format for voice output.
     * Example: "From the home screen, tap Menu, then Settings, then Security, then Touch ID"
     */
    public String toVoiceFormat() {
        if (pathSegments.isEmpty()) {
            return "This feature is on the home screen.";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("From ");
        sb.append(startingPoint != null ? startingPoint : "the home screen");
        sb.append(", tap ");
        
        for (int i = 0; i < pathSegments.size(); i++) {
            if (i > 0 && i < pathSegments.size() - 1) {
                sb.append(", then ");
            } else if (i == pathSegments.size() - 1 && i > 0) {
                sb.append(", then ");
            }
            sb.append(pathSegments.get(i));
        }
        sb.append(".");
        
        return sb.toString();
    }

    /**
     * Get the number of taps required to reach the feature.
     */
    public int getTapCount() {
        return pathSegments.size();
    }

    /**
     * Convert to Map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("featureId", featureId);
        map.put("platform", platform.name());
        map.put("startingPoint", startingPoint);
        map.put("pathSegments", pathSegments);
        map.put("formattedPath", getFormattedPath());
        map.put("tapCount", getTapCount());
        map.put("voiceResponse", toVoiceFormat());
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
        private Platform platform = Platform.BOTH;
        private List<String> pathSegments = new ArrayList<>();
        private String startingPoint = "Home";
        private String deepLink;

        public Builder featureId(String featureId) { this.featureId = featureId; return this; }
        public Builder platform(Platform platform) { this.platform = platform; return this; }
        public Builder pathSegments(List<String> pathSegments) { this.pathSegments = new ArrayList<>(pathSegments); return this; }
        public Builder addSegment(String segment) { this.pathSegments.add(segment); return this; }
        public Builder startingPoint(String startingPoint) { this.startingPoint = startingPoint; return this; }
        public Builder deepLink(String deepLink) { this.deepLink = deepLink; return this; }

        public NavigationPath build() {
            if (featureId == null || featureId.isBlank()) {
                throw new IllegalStateException("featureId is required");
            }
            return new NavigationPath(this);
        }
    }
}
