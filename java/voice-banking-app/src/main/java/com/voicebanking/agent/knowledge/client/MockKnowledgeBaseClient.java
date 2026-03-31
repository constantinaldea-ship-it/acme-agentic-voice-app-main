package com.voicebanking.agent.knowledge.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebanking.agent.knowledge.domain.KnowledgeArticle;
import com.voicebanking.agent.knowledge.domain.KnowledgeCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Mock implementation of KnowledgeBaseClient that loads articles from a static JSON file.
 * Used for MVP development and testing. Will be replaced with real KB integration.
 */
@Component
public class MockKnowledgeBaseClient implements KnowledgeBaseClient {

    private static final Logger log = LoggerFactory.getLogger(MockKnowledgeBaseClient.class);
    private static final String KNOWLEDGE_BASE_PATH = "knowledge/knowledge-base.json";

    private final Map<String, KnowledgeArticle> articlesById = new ConcurrentHashMap<>();
    private final Map<KnowledgeCategory, List<KnowledgeArticle>> articlesByCategory = new ConcurrentHashMap<>();
    private final List<String> bicCodes = new ArrayList<>();
    private final ObjectMapper objectMapper;

    public MockKnowledgeBaseClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        loadKnowledgeBase();
    }

    private void loadKnowledgeBase() {
        try {
            ClassPathResource resource = new ClassPathResource(KNOWLEDGE_BASE_PATH);
            try (InputStream inputStream = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(inputStream);
                loadArticles(root.get("articles"));
                loadBicCodes(root.get("bicCodes"));
                log.info("Loaded {} knowledge articles and {} BIC codes",
                        articlesById.size(), bicCodes.size());
            }
        } catch (IOException e) {
            log.error("Failed to load knowledge base from {}", KNOWLEDGE_BASE_PATH, e);
            throw new RuntimeException("Failed to initialize knowledge base", e);
        }
    }

    private void loadArticles(JsonNode articlesNode) {
        if (articlesNode == null || !articlesNode.isArray()) {
            log.warn("No articles found in knowledge base");
            return;
        }

        for (JsonNode articleNode : articlesNode) {
            try {
                KnowledgeArticle article = parseArticle(articleNode);
                articlesById.put(article.articleId(), article);
                articlesByCategory
                        .computeIfAbsent(article.category(), k -> new ArrayList<>())
                        .add(article);
            } catch (Exception e) {
                log.warn("Failed to parse article: {}", articleNode, e);
            }
        }
    }

    private KnowledgeArticle parseArticle(JsonNode node) {
        String articleId = node.get("articleId").asText();
        KnowledgeCategory category = KnowledgeCategory.valueOf(node.get("category").asText());
        String title = node.get("title").asText();
        String content = node.get("content").asText();
        String summary = node.get("summary").asText();

        List<String> keywords = new ArrayList<>();
        JsonNode keywordsNode = node.get("keywords");
        if (keywordsNode != null && keywordsNode.isArray()) {
            for (JsonNode keyword : keywordsNode) {
                keywords.add(keyword.asText().toLowerCase());
            }
        }

        return KnowledgeArticle.withoutEmbedding(
                articleId, category, title, content, summary, keywords, Instant.now()
        );
    }

    private void loadBicCodes(JsonNode bicNode) {
        if (bicNode == null || !bicNode.isObject()) {
            return;
        }
        bicNode.fieldNames().forEachRemaining(bicCodes::add);
    }

    @Override
    public Optional<KnowledgeArticle> getArticleById(String articleId) {
        return Optional.ofNullable(articlesById.get(articleId));
    }

    @Override
    public List<KnowledgeArticle> getArticlesByCategory(KnowledgeCategory category) {
        return articlesByCategory.getOrDefault(category, Collections.emptyList());
    }

    @Override
    public List<KnowledgeArticle> getAllArticles() {
        return new ArrayList<>(articlesById.values());
    }

    @Override
    public List<KnowledgeArticle> searchByKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return Collections.emptyList();
        }

        Set<String> searchTerms = keywords.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        return articlesById.values().stream()
                .filter(article -> hasMatchingKeyword(article, searchTerms))
                .collect(Collectors.toList());
    }

    private boolean hasMatchingKeyword(KnowledgeArticle article, Set<String> searchTerms) {
        for (String keyword : article.keywords()) {
            if (searchTerms.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<KnowledgeArticle> searchByTitle(String titlePattern) {
        if (titlePattern == null || titlePattern.isBlank()) {
            return Collections.emptyList();
        }

        String pattern = titlePattern.toLowerCase();
        return articlesById.values().stream()
                .filter(article -> article.title().toLowerCase().contains(pattern))
                .collect(Collectors.toList());
    }

    @Override
    public int getArticleCount() {
        return articlesById.size();
    }

    @Override
    public List<String> getAvailableBicCodes() {
        return Collections.unmodifiableList(bicCodes);
    }
}
