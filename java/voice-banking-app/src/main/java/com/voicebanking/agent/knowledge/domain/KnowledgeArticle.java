package com.voicebanking.agent.knowledge.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record KnowledgeArticle(
    @NotBlank String articleId,
    @NotNull KnowledgeCategory category,
    @NotBlank @Size(max = 200) String title,
    @NotBlank String content,
    @NotBlank @Size(max = 500) String summary,
    @NotNull List<String> keywords,
    @NotNull Instant lastUpdated,
    float[] embedding
) {
    public static KnowledgeArticle withoutEmbedding(
            String articleId, KnowledgeCategory category, String title,
            String content, String summary, List<String> keywords, Instant lastUpdated) {
        return new KnowledgeArticle(articleId, category, title, content, summary, keywords, lastUpdated, null);
    }

    public KnowledgeArticle withEmbedding(float[] newEmbedding) {
        return new KnowledgeArticle(articleId, category, title, content, summary, keywords, lastUpdated, newEmbedding);
    }

    public boolean hasEmbedding() {
        return embedding != null && embedding.length > 0;
    }
}
