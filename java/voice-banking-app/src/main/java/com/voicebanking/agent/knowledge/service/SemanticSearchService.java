package com.voicebanking.agent.knowledge.service;

import com.voicebanking.agent.knowledge.client.KnowledgeBaseClient;
import com.voicebanking.agent.knowledge.domain.*;
import com.voicebanking.agent.knowledge.domain.SearchResult.MatchType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Semantic search service for knowledge articles.
 * MVP implementation uses keyword matching with TF-IDF-like scoring.
 * Future: Integrate with Vertex AI embeddings for true semantic search.
 */
@Service
public class SemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);
    private static final double DEFAULT_MIN_SCORE = 0.3;
    private static final int DEFAULT_MAX_RESULTS = 5;

    private final KnowledgeBaseClient knowledgeBaseClient;
    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> documentFrequency = new ConcurrentHashMap<>();
    private int totalDocuments = 0;

    public SemanticSearchService(KnowledgeBaseClient knowledgeBaseClient) {
        this.knowledgeBaseClient = knowledgeBaseClient;
    }

    @PostConstruct
    public void init() {
        buildIndex();
    }

    private void buildIndex() {
        List<KnowledgeArticle> articles = knowledgeBaseClient.getAllArticles();
        totalDocuments = articles.size();

        // Build document frequency for IDF calculation
        for (KnowledgeArticle article : articles) {
            Set<String> uniqueTerms = extractTerms(article);
            for (String term : uniqueTerms) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }

        log.info("Built search index with {} documents and {} unique terms",
                totalDocuments, documentFrequency.size());
    }

    private Set<String> extractTerms(KnowledgeArticle article) {
        Set<String> terms = new HashSet<>();
        terms.addAll(article.keywords());
        terms.addAll(tokenize(article.title()));
        terms.addAll(tokenize(article.content()));
        return terms.stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(text.toLowerCase().split("\\W+"))
                .filter(word -> word.length() > 2)
                .collect(Collectors.toList());
    }

    /**
     * Search for articles matching the query.
     * Uses keyword matching with TF-IDF scoring for MVP.
     */
    public List<SearchResult> search(KnowledgeQuery query) {
        if (query == null || query.queryText() == null || query.queryText().isBlank()) {
            return Collections.emptyList();
        }

        List<String> queryTerms = tokenize(query.queryText());
        if (queryTerms.isEmpty()) {
            return Collections.emptyList();
        }

        double minScore = query.minScore() > 0 ? query.minScore() : DEFAULT_MIN_SCORE;
        int maxResults = query.maxResults() > 0 ? query.maxResults() : DEFAULT_MAX_RESULTS;

        List<KnowledgeArticle> candidateArticles = getCandidateArticles(query);
        List<SearchResult> results = new ArrayList<>();

        for (KnowledgeArticle article : candidateArticles) {
            SearchResult result = scoreArticle(article, queryTerms);
            if (result != null && result.score() >= minScore) {
                results.add(result);
            }
        }

        // Sort by score descending and limit results
        return results.stream()
                .sorted(Comparator.comparingDouble(SearchResult::score).reversed())
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    private List<KnowledgeArticle> getCandidateArticles(KnowledgeQuery query) {
        if (query.category() != null) {
            return knowledgeBaseClient.getArticlesByCategory(query.category());
        }
        return knowledgeBaseClient.getAllArticles();
    }

    private SearchResult scoreArticle(KnowledgeArticle article, List<String> queryTerms) {
        double keywordScore = calculateKeywordScore(article, queryTerms);
        double titleScore = calculateTitleScore(article, queryTerms);
        double contentScore = calculateContentScore(article, queryTerms);

        // Weighted combination of scores
        double finalScore = (keywordScore * 0.5) + (titleScore * 0.3) + (contentScore * 0.2);

        if (finalScore <= 0) {
            return null;
        }

        // Determine match type
        MatchType matchType = determineMatchType(keywordScore, titleScore, contentScore);

        return new SearchResult(article, Math.min(finalScore, 1.0), matchType);
    }

    private double calculateKeywordScore(KnowledgeArticle article, List<String> queryTerms) {
        if (article.keywords() == null || article.keywords().isEmpty()) {
            return 0;
        }

        Set<String> articleKeywords = article.keywords().stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        int matches = 0;
        for (String term : queryTerms) {
            if (articleKeywords.contains(term)) {
                matches++;
            }
        }

        return queryTerms.isEmpty() ? 0 : (double) matches / queryTerms.size();
    }

    private double calculateTitleScore(KnowledgeArticle article, List<String> queryTerms) {
        List<String> titleTerms = tokenize(article.title());
        return calculateTermOverlap(titleTerms, queryTerms);
    }

    private double calculateContentScore(KnowledgeArticle article, List<String> queryTerms) {
        List<String> contentTerms = tokenize(article.content());
        return calculateTermOverlap(contentTerms, queryTerms) * calculateIdfBoost(queryTerms);
    }

    private double calculateTermOverlap(List<String> docTerms, List<String> queryTerms) {
        if (docTerms.isEmpty() || queryTerms.isEmpty()) {
            return 0;
        }

        Set<String> docTermSet = new HashSet<>(docTerms);
        int matches = 0;
        for (String term : queryTerms) {
            if (docTermSet.contains(term)) {
                matches++;
            }
        }

        return (double) matches / queryTerms.size();
    }

    private double calculateIdfBoost(List<String> queryTerms) {
        if (totalDocuments == 0) {
            return 1.0;
        }

        double idfSum = 0;
        for (String term : queryTerms) {
            int df = documentFrequency.getOrDefault(term, 1);
            idfSum += Math.log((double) totalDocuments / df);
        }

        // Normalize IDF to [0.5, 1.5] range
        double avgIdf = idfSum / queryTerms.size();
        return 0.5 + (avgIdf / (avgIdf + 1));
    }

    private MatchType determineMatchType(double keywordScore, double titleScore, double contentScore) {
        if (keywordScore >= 0.8) {
            return MatchType.EXACT;
        } else if (titleScore >= 0.5 || keywordScore >= 0.5) {
            return MatchType.KEYWORD;
        } else {
            return MatchType.SEMANTIC;
        }
    }

    /**
     * Search specifically for BIC/SWIFT code information.
     */
    public Optional<SearchResult> searchBicCode(String bicCode) {
        if (bicCode == null || bicCode.isBlank()) {
            return Optional.empty();
        }

        String normalizedBic = bicCode.toUpperCase().trim();

        // First check if it's a known BIC code
        List<String> knownBics = knowledgeBaseClient.getAvailableBicCodes();
        boolean isKnownBic = knownBics.stream()
                .anyMatch(bic -> bic.equalsIgnoreCase(normalizedBic));

        // Search for BIC-related articles
        KnowledgeQuery query = KnowledgeQuery.withDefaults("BIC SWIFT code " + normalizedBic);
        List<SearchResult> results = search(query);

        if (!results.isEmpty()) {
            SearchResult topResult = results.get(0);
            // Boost score if it's a known BIC
            if (isKnownBic) {
                return Optional.of(new SearchResult(
                        topResult.article(),
                        Math.min(topResult.score() + 0.2, 1.0),
                        MatchType.EXACT
                ));
            }
            return Optional.of(topResult);
        }

        return Optional.empty();
    }

    /**
     * Search for FAQ articles.
     */
    public List<SearchResult> searchFaq(String question) {
        KnowledgeQuery query = KnowledgeQuery.forCategory(question, KnowledgeCategory.FAQ);
        return search(query);
    }

    /**
     * Search for app guidance articles.
     */
    public List<SearchResult> searchAppGuidance(String topic) {
        KnowledgeQuery query = KnowledgeQuery.forCategory(topic, KnowledgeCategory.APP_GUIDANCE);
        return search(query);
    }
}
