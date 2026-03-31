package com.voicebanking.agent.knowledge;

import com.voicebanking.agent.knowledge.domain.*;
import com.voicebanking.agent.knowledge.domain.KnowledgeQuery.OutputFormat;
import com.voicebanking.agent.knowledge.service.KnowledgeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for KnowledgeFormatter.
 * Tests voice-optimized formatting and response generation.
 */
class KnowledgeFormatterTest {

    private KnowledgeFormatter formatter;

    @BeforeEach
    void setUp() {
        formatter = new KnowledgeFormatter();
    }

    @Nested
    @DisplayName("Single Result Formatting Tests")
    class SingleResultFormattingTests {

        @Test
        @DisplayName("Should format result for voice output")
        void testFormatForVoice() {
            SearchResult result = createTestResult(0.9, SearchResult.MatchType.KEYWORD);

            FormattedKnowledge formatted = formatter.format(result, OutputFormat.VOICE);

            assertNotNull(formatted);
            assertNotNull(formatted.voiceResponse());
            assertFalse(formatted.voiceResponse().isEmpty());
            assertTrue(formatted.wordCount() > 0);
        }

        @Test
        @DisplayName("Should format result for text output")
        void testFormatForText() {
            SearchResult result = createTestResult(0.9, SearchResult.MatchType.KEYWORD);

            FormattedKnowledge formatted = formatter.format(result, OutputFormat.TEXT);

            assertNotNull(formatted);
            assertNotNull(formatted.textResponse());
            assertTrue(formatted.textResponse().contains("**")); // Markdown formatting
        }

        @Test
        @DisplayName("Should handle null result gracefully")
        void testNullResult() {
            FormattedKnowledge formatted = formatter.format(null, OutputFormat.VOICE);
            assertNull(formatted);
        }

        @Test
        @DisplayName("Should indicate hasMoreDetail for long content")
        void testHasMoreDetailFlag() {
            // Create article with long content
            KnowledgeArticle longArticle = KnowledgeArticle.withoutEmbedding(
                    "KB-LONG",
                    KnowledgeCategory.GENERAL_INFO,
                    "Long Article",
                    generateLongContent(200), // 200 words
                    "Short summary",
                    List.of("test"),
                    Instant.now()
            );
            SearchResult result = new SearchResult(longArticle, 0.9, SearchResult.MatchType.KEYWORD);

            FormattedKnowledge formatted = formatter.format(result, OutputFormat.VOICE);

            assertTrue(formatted.hasMoreDetail());
        }

        @Test
        @DisplayName("Should preserve category in formatted output")
        void testCategoryPreserved() {
            KnowledgeArticle article = KnowledgeArticle.withoutEmbedding(
                    "KB-001",
                    KnowledgeCategory.HOW_TO,
                    "How to Transfer",
                    "Step by step guide",
                    "Transfer money easily",
                    List.of("transfer"),
                    Instant.now()
            );
            SearchResult result = new SearchResult(article, 0.85, SearchResult.MatchType.KEYWORD);

            FormattedKnowledge formatted = formatter.format(result, OutputFormat.VOICE);

            assertEquals(KnowledgeCategory.HOW_TO, formatted.category());
        }
    }

    @Nested
    @DisplayName("Multiple Results Formatting Tests")
    class MultipleResultsFormattingTests {

        @Test
        @DisplayName("Should format single result directly")
        void testSingleResultFormatting() {
            List<SearchResult> results = List.of(createTestResult(0.9, SearchResult.MatchType.KEYWORD));

            String response = formatter.formatMultipleResults(results, OutputFormat.VOICE);

            assertNotNull(response);
            assertFalse(response.contains("I found"));
        }

        @Test
        @DisplayName("Should summarize multiple results")
        void testMultipleResultsSummary() {
            List<SearchResult> results = List.of(
                    createTestResult(0.9, SearchResult.MatchType.KEYWORD),
                    createTestResult(0.8, SearchResult.MatchType.SEMANTIC),
                    createTestResult(0.7, SearchResult.MatchType.SEMANTIC)
            );

            String response = formatter.formatMultipleResults(results, OutputFormat.VOICE);

            assertTrue(response.contains("I found"));
            assertTrue(response.contains("3"));
        }

        @Test
        @DisplayName("Should handle empty results list")
        void testEmptyResults() {
            String response = formatter.formatMultipleResults(List.of(), OutputFormat.VOICE);

            assertTrue(response.contains("couldn't find"));
        }

        @Test
        @DisplayName("Should handle null results list")
        void testNullResults() {
            String response = formatter.formatMultipleResults(null, OutputFormat.VOICE);

            assertTrue(response.contains("couldn't find"));
        }

        @Test
        @DisplayName("Should offer more detail for many results")
        void testMoreDetailOffer() {
            List<SearchResult> results = List.of(
                    createTestResult(0.9, SearchResult.MatchType.KEYWORD),
                    createTestResult(0.8, SearchResult.MatchType.SEMANTIC),
                    createTestResult(0.7, SearchResult.MatchType.SEMANTIC),
                    createTestResult(0.6, SearchResult.MatchType.SEMANTIC),
                    createTestResult(0.5, SearchResult.MatchType.SEMANTIC)
            );

            String response = formatter.formatMultipleResults(results, OutputFormat.VOICE);

            assertTrue(response.contains("more detail") || response.contains("Would you like"));
        }
    }

    @Nested
    @DisplayName("BIC Response Formatting Tests")
    class BicResponseTests {

        @Test
        @DisplayName("Should format valid BIC response")
        void testValidBicResponse() {
            String response = formatter.formatBicResponse("ACMEDEXX", true);

            assertTrue(response.contains("ACMEDEXX"));
            assertTrue(response.contains("valid") || response.contains("use"));
        }

        @Test
        @DisplayName("Should format invalid BIC response")
        void testInvalidBicResponse() {
            String response = formatter.formatBicResponse("UNKNOWN123", false);

            assertTrue(response.contains("UNKNOWN123"));
            assertTrue(response.contains("couldn't verify") || response.contains("ACMEDEXX"));
        }
    }

    @Nested
    @DisplayName("No Results Formatting Tests")
    class NoResultsTests {

        @Test
        @DisplayName("Should format no results message")
        void testNoResultsMessage() {
            String response = formatter.formatNoResults("obscure banking term");

            assertTrue(response.contains("couldn't find"));
            assertTrue(response.contains("obscure"));
        }

        @Test
        @DisplayName("Should truncate long query in no results message")
        void testLongQueryTruncation() {
            String longQuery = "this is a very long query that should be truncated because it exceeds the reasonable length for display";
            String response = formatter.formatNoResults(longQuery);

            // Response should be generated and contain expected message
            assertNotNull(response);
            assertTrue(response.contains("couldn't find"));
        }
    }

    @Nested
    @DisplayName("FAQ Topics Formatting Tests")
    class FaqTopicsTests {

        @Test
        @DisplayName("Should format FAQ topics list")
        void testFaqTopicsFormatting() {
            List<SearchResult> results = List.of(
                    createTestResult(0.9, SearchResult.MatchType.KEYWORD),
                    createTestResult(0.8, SearchResult.MatchType.KEYWORD)
            );

            String response = formatter.formatFaqTopics(results);

            assertTrue(response.contains("questions") || response.contains("help"));
        }

        @Test
        @DisplayName("Should handle empty FAQ list")
        void testEmptyFaqTopics() {
            String response = formatter.formatFaqTopics(List.of());

            assertTrue(response.contains("don't have") || response.contains("What else"));
        }
    }

    @Nested
    @DisplayName("Voice Optimization Tests")
    class VoiceOptimizationTests {

        @Test
        @DisplayName("Should keep voice responses concise")
        void testConciseVoiceResponse() {
            SearchResult result = createTestResult(0.9, SearchResult.MatchType.KEYWORD);

            FormattedKnowledge formatted = formatter.format(result, OutputFormat.VOICE);

            assertTrue(formatted.isConcise()); // <= 50 words
        }

        @Test
        @DisplayName("Should not exceed max voice word count")
        void testMaxVoiceWords() {
            KnowledgeArticle longArticle = KnowledgeArticle.withoutEmbedding(
                    "KB-LONG",
                    KnowledgeCategory.GENERAL_INFO,
                    "Long Article",
                    generateLongContent(200),
                    "Short summary for voice output",
                    List.of("test"),
                    Instant.now()
            );
            SearchResult result = new SearchResult(longArticle, 0.9, SearchResult.MatchType.KEYWORD);

            FormattedKnowledge formatted = formatter.format(result, OutputFormat.VOICE);

            // Voice response should be within limits
            assertFalse(formatted.isTooLongForVoice());
        }
    }

    // ========== Helper Methods ==========

    private SearchResult createTestResult(double score, SearchResult.MatchType matchType) {
        KnowledgeArticle article = KnowledgeArticle.withoutEmbedding(
                "KB-TEST-" + System.nanoTime(),
                KnowledgeCategory.GENERAL_INFO,
                "Test Article Title",
                "This is the test content for the article.",
                "Brief summary of the article",
                List.of("test", "article"),
                Instant.now()
        );
        return new SearchResult(article, score, matchType);
    }

    private String generateLongContent(int wordCount) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < wordCount; i++) {
            sb.append("word").append(i).append(" ");
        }
        return sb.toString().trim();
    }
}
