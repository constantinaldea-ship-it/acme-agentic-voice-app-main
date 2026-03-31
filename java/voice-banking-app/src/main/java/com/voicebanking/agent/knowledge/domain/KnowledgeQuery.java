package com.voicebanking.agent.knowledge.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record KnowledgeQuery(
    @NotBlank @Size(min = 1, max = 500) String queryText,
    KnowledgeCategory category,
    @Min(1) @Max(20) int maxResults,
    @Min(0) @Max(1) double minScore,
    OutputFormat outputFormat
) {
    public enum OutputFormat {
        VOICE,
        TEXT
    }

    public static KnowledgeQuery withDefaults(String queryText) {
        return new KnowledgeQuery(queryText, null, 5, 0.5, OutputFormat.VOICE);
    }

    public static KnowledgeQuery forCategory(String queryText, KnowledgeCategory category) {
        return new KnowledgeQuery(queryText, category, 5, 0.5, OutputFormat.VOICE);
    }
}
