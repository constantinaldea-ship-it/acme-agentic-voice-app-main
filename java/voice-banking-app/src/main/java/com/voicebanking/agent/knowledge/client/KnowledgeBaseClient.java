package com.voicebanking.agent.knowledge.client;

import com.voicebanking.agent.knowledge.domain.KnowledgeArticle;
import com.voicebanking.agent.knowledge.domain.KnowledgeCategory;
import java.util.List;
import java.util.Optional;

public interface KnowledgeBaseClient {

    Optional<KnowledgeArticle> getArticleById(String articleId);

    List<KnowledgeArticle> getArticlesByCategory(KnowledgeCategory category);

    List<KnowledgeArticle> getAllArticles();

    List<KnowledgeArticle> searchByKeywords(List<String> keywords);

    List<KnowledgeArticle> searchByTitle(String titlePattern);

    int getArticleCount();

    List<String> getAvailableBicCodes();
}
