package com.voicebanking.agent.knowledge.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SearchResult(
    @NotNull KnowledgeArticle article,
    @Min(0) @Max(1) double score,
    @NotNull MatchType matchType
) {
    public enum MatchType {
        SEMANTIC,
        KEYWORD,
        EXACT
    }

    public boolean isHighConfidence() {
        return score >= 0.85;
    }

    public boolean isAboveThreshold(double threshold) {
        return score >= threshold;
    }
}
