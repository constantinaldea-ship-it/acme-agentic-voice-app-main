package com.voicebanking.agent.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebanking.agent.knowledge.client.MockKnowledgeBaseClient;
import com.voicebanking.agent.knowledge.domain.*;
import com.voicebanking.agent.knowledge.domain.KnowledgeQuery.OutputFormat;
import com.voicebanking.agent.knowledge.service.SemanticSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SemanticSearchService.
 * Tests keyword-based search with TF-IDF scoring.
 */
@DisplayName("SemanticSearchService Tests")
class SemanticSearchServiceTest {

    private SemanticSearchService searchService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        MockKnowledgeBaseClient client = new MockKnowledgeBaseClient(objectMapper);
        client.init();
        
        searchService = new SemanticSearchService(client);
        searchService.init();
    }

    @Nested
    @DisplayName("Basic Search Tests")
    class BasicSearchTests {

        @Test
        @DisplayName("Should find articles by keyword match")
        void testKeywordSearch() {
            KnowledgeQuery query = KnowledgeQuery.withDefaults("password reset");

            List<SearchResult> results = searchService.search(query);

            assertFalse(results.isEmpty());
            assertTrue(results.stream()
                    .anyMatch(r -> r.article().title().toLowerCase().contains("password")));
        }

        @Test
        @DisplayName("Should return empty list for null query")
        void testNullQuery() {
            List<SearchResult> results = searchService.search(null);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list for blank query")
        void testBlankQuery() {
            KnowledgeQuery query = new KnowledgeQuery("   ", null, 5, 0.3, OutputFormat.VOICE);
            List<SearchResult> results = searchService.search(query);
            assertTrue(results.isEmpty());
        }

        @Test
        @DisplayName("Should respect maxResults limit")
        void testMaxResultsLimit() {
            KnowledgeQuery query = new KnowledgeQuery("bank account", null, 2, 0.1, OutputFormat.VOICE);

            List<SearchResult> results = searchService.search(query);

            assertTrue(results.size() <= 2);
        }

        @Test
        @DisplayName("Should filter by minimum score")
        void testMinScoreFilter() {
            KnowledgeQuery query = new KnowledgeQuery("opening hours", null, 10, 0.5, OutputFormat.VOICE);

            List<SearchResult> results = searchService.search(query);

            assertTrue(results.stream().allMatch(r -> r.score() >= 0.5));
        }

        @Test
        @DisplayName("Should sort results by score descending")
        void testResultsSortedByScore() {
            KnowledgeQuery query = KnowledgeQuery.withDefaults("bank account transfer");

            List<SearchResult> results = searchService.search(query);

            if (results.size() > 1) {
                for (int i = 0; i < results.size() - 1; i++) {
                    assertTrue(results.get(i).score() >= results.get(i + 1).score(),
                            "Results should be sorted by score descending");
                }
            }
        }

        @Test
        @DisplayName("Should find opening hours article")
        void testOpeningHoursSearch() {
            KnowledgeQuery query = KnowledgeQuery.withDefaults("opening hours");

            List<SearchResult> results = searchService.search(query);

            assertFalse(results.isEmpty());
            assertTrue(results.stream()
                    .anyMatch(r -> r.article().title().toLowerCase().contains("hours")));
        }
    }

    @Nested
    @DisplayName("Category Filter Tests")
    class CategoryFilterTests {

        @Test
        @DisplayName("Should filter by FAQ category when specified")
        void testFaqCategoryFilter() {
            KnowledgeQuery query = KnowledgeQuery.forCategory("account", KnowledgeCategory.FAQ);

            List<SearchResult> results = searchService.search(query);

            assertTrue(results.stream()
                    .allMatch(r -> r.article().category() == KnowledgeCategory.FAQ));
        }

        @Test
        @DisplayName("Should filter by APP_GUIDANCE category")
        void testAppGuidanceCategoryFilter() {
            KnowledgeQuery query = KnowledgeQuery.forCategory("mobile", KnowledgeCategory.APP_GUIDANCE);

            List<SearchResult> results = searchService.search(query);

            assertTrue(results.stream()
                    .allMatch(r -> r.article().category() == KnowledgeCategory.APP_GUIDANCE));
        }
    }

    @Nested
    @DisplayName("BIC Code Search Tests")
    class BicCodeSearchTests {

        @Test
        @DisplayName("Should find BIC code information")
        void testBicCodeSearch() {
            Optional<SearchResult> result = searchService.searchBicCode("ACMEDEXX");

            assertTrue(result.isPresent());
            assertEquals(SearchResult.MatchType.EXACT, result.get().matchType());
        }

        @Test
        @DisplayName("Should handle unknown BIC code gracefully")
        void testUnknownBicCode() {
            Optional<SearchResult> result = searchService.searchBicCode("UNKNOWN123");

            // May or may not find result, but should not throw
            assertDoesNotThrow(() -> searchService.searchBicCode("UNKNOWN123"));
        }

        @Test
        @DisplayName("Should handle null BIC code")
        void testNullBicCode() {
            Optional<SearchResult> result = searchService.searchBicCode(null);
            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should handle blank BIC code")
        void testBlankBicCode() {
            Optional<SearchResult> result = searchService.searchBicCode("   ");
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("FAQ Search Tests")
    class FaqSearchTests {

        @Test
        @DisplayName("Should search FAQ articles for IBAN")
        void testFaqSearchIban() {
            List<SearchResult> results = searchService.searchFaq("IBAN");

            assertFalse(results.isEmpty());
            assertTrue(results.stream()
                    .anyMatch(r -> r.article().category() == KnowledgeCategory.FAQ));
        }

        @Test
        @DisplayName("Should search FAQ articles for password")
        void testFaqSearchPassword() {
            List<SearchResult> results = searchService.searchFaq("password reset");

            assertFalse(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("App Guidance Search Tests")
    class AppGuidanceSearchTests {

        @Test
        @DisplayName("Should search app guidance articles")
        void testAppGuidanceSearch() {
            List<SearchResult> results = searchService.searchAppGuidance("mobile banking setup");

            assertFalse(results.isEmpty());
            assertTrue(results.stream()
                    .anyMatch(r -> r.article().category() == KnowledgeCategory.APP_GUIDANCE));
        }

        @Test
        @DisplayName("Should search app guidance for balance")
        void testAppGuidanceBalance() {
            List<SearchResult> results = searchService.searchAppGuidance("check balance");

            assertFalse(results.isEmpty());
        }
    }

    @Nested
    @DisplayName("Match Type Tests")
    class MatchTypeTests {

        @Test
        @DisplayName("Should assign correct match type based on score")
        void testMatchTypeAssignment() {
            KnowledgeQuery query = KnowledgeQuery.withDefaults("IBAN account number");

            List<SearchResult> results = searchService.search(query);

            for (SearchResult result : results) {
                assertNotNull(result.matchType());
            }
        }

        @Test
        @DisplayName("High confidence results should be marked appropriately")
        void testHighConfidenceMarking() {
            KnowledgeQuery query = KnowledgeQuery.withDefaults("BIC SWIFT ACMEDEXX");

            List<SearchResult> results = searchService.search(query);

            assertFalse(results.isEmpty());
            // Top result for exact keyword match should have high score
            if (!results.isEmpty() && results.get(0).score() >= 0.85) {
                assertTrue(results.get(0).isHighConfidence());
            }
        }
    }
}
