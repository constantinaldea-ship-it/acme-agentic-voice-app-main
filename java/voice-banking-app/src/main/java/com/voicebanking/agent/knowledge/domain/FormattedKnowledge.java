package com.voicebanking.agent.knowledge.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FormattedKnowledge(
    @NotBlank String articleId,
    @NotBlank String voiceResponse,
    @NotBlank String textResponse,
    boolean hasMoreDetail,
    @NotNull KnowledgeCategory category,
    @Min(1) int wordCount
) {
    public static FormattedKnowledge forVoice(
            String articleId, String voiceResponse, KnowledgeCategory category, boolean hasMoreDetail) {
        int words = voiceResponse.split("\\s+").length;
        return new FormattedKnowledge(articleId, voiceResponse, voiceResponse, hasMoreDetail, category, words);
    }

    public boolean isConcise() {
        return wordCount <= 50;
    }

    public boolean isTooLongForVoice() {
        return wordCount > 100;
    }
}
