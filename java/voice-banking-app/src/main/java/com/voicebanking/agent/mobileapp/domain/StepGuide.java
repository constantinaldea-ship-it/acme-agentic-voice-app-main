package com.voicebanking.agent.mobileapp.domain;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A complete step-by-step guide for using a mobile app feature.
 * 
 * Guides are designed to be voice-friendly with clear navigation
 * and pacing suitable for verbal instruction.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class StepGuide {
    private final String featureId;
    private final String featureName;
    private final Platform platform;
    private final List<String> prerequisites;
    private final List<Step> steps;
    private final String alternativePath;
    private final String helpArticleUrl;
    private final String voiceSummary;

    private StepGuide(Builder builder) {
        this.featureId = builder.featureId;
        this.featureName = builder.featureName;
        this.platform = builder.platform;
        this.prerequisites = builder.prerequisites != null ? List.copyOf(builder.prerequisites) : List.of();
        this.steps = builder.steps != null ? List.copyOf(builder.steps) : List.of();
        this.alternativePath = builder.alternativePath;
        this.helpArticleUrl = builder.helpArticleUrl;
        this.voiceSummary = builder.voiceSummary;
    }

    // Getters
    public String getFeatureId() { return featureId; }
    public String getFeatureName() { return featureName; }
    public Platform getPlatform() { return platform; }
    public List<String> getPrerequisites() { return prerequisites; }
    public List<Step> getSteps() { return steps; }
    public String getAlternativePath() { return alternativePath; }
    public String getHelpArticleUrl() { return helpArticleUrl; }
    public String getVoiceSummary() { return voiceSummary; }

    public int getTotalSteps() {
        return steps.size();
    }

    /**
     * Format the guide for voice output.
     * Includes pacing-friendly structure with step count introduction.
     */
    public String toVoiceFormat() {
        StringBuilder sb = new StringBuilder();
        
        // Introduction
        sb.append(String.format("To %s, follow these %d steps. ", 
                featureName.toLowerCase(), steps.size()));
        
        // Prerequisites if any
        if (!prerequisites.isEmpty()) {
            sb.append("First, make sure you have ");
            sb.append(String.join(" and ", prerequisites));
            sb.append(". ");
        }
        
        // Steps
        for (Step step : steps) {
            sb.append(step.toVoiceFormat()).append(". ");
        }
        
        // Closing
        sb.append("Would you like me to repeat any step?");
        
        return sb.toString();
    }

    /**
     * Get a specific step by number (1-indexed).
     */
    public Optional<Step> getStep(int stepNumber) {
        if (stepNumber < 1 || stepNumber > steps.size()) {
            return Optional.empty();
        }
        return Optional.of(steps.get(stepNumber - 1));
    }

    /**
     * Convert to Map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("featureId", featureId);
        map.put("featureName", featureName);
        map.put("platform", platform.name());
        map.put("totalSteps", getTotalSteps());
        map.put("prerequisites", prerequisites);
        map.put("steps", steps.stream().map(Step::toMap).collect(Collectors.toList()));
        if (alternativePath != null) {
            map.put("alternativePath", alternativePath);
        }
        if (helpArticleUrl != null) {
            map.put("helpArticleUrl", helpArticleUrl);
        }
        map.put("voiceResponse", toVoiceFormat());
        return map;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String featureId;
        private String featureName;
        private Platform platform = Platform.BOTH;
        private List<String> prerequisites;
        private List<Step> steps = new ArrayList<>();
        private String alternativePath;
        private String helpArticleUrl;
        private String voiceSummary;

        public Builder featureId(String featureId) { this.featureId = featureId; return this; }
        public Builder featureName(String featureName) { this.featureName = featureName; return this; }
        public Builder platform(Platform platform) { this.platform = platform; return this; }
        public Builder prerequisites(List<String> prerequisites) { this.prerequisites = prerequisites; return this; }
        public Builder steps(List<Step> steps) { this.steps = new ArrayList<>(steps); return this; }
        public Builder addStep(Step step) { this.steps.add(step); return this; }
        public Builder addStep(int number, String instruction) { 
            this.steps.add(Step.simple(number, instruction)); 
            return this; 
        }
        public Builder alternativePath(String alternativePath) { this.alternativePath = alternativePath; return this; }
        public Builder helpArticleUrl(String helpArticleUrl) { this.helpArticleUrl = helpArticleUrl; return this; }
        public Builder voiceSummary(String voiceSummary) { this.voiceSummary = voiceSummary; return this; }

        public StepGuide build() {
            if (featureId == null || featureId.isBlank()) {
                throw new IllegalStateException("featureId is required");
            }
            if (featureName == null || featureName.isBlank()) {
                throw new IllegalStateException("featureName is required");
            }
            if (steps.isEmpty()) {
                throw new IllegalStateException("at least one step is required");
            }
            return new StepGuide(this);
        }
    }
}
