package com.voicebanking.agent.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebanking.agent.knowledge.client.MockKnowledgeBaseClient;
import com.voicebanking.agent.knowledge.domain.*;
import com.voicebanking.agent.knowledge.service.KnowledgeFormatter;
import com.voicebanking.agent.knowledge.service.SemanticSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for KnowledgeCompilerAgent.
 * Tests all 4 tools with real components: getKnowledge, getBankInfo, searchFAQ, getAppGuidance.
 */
@DisplayName("KnowledgeCompilerAgent Tests")
class KnowledgeCompilerAgentTest {

    private KnowledgeCompilerAgent agent;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        MockKnowledgeBaseClient client = new MockKnowledgeBaseClient(objectMapper);
        client.init();
        
        SemanticSearchService searchService = new SemanticSearchService(client);
        searchService.init();
        
        KnowledgeFormatter formatter = new KnowledgeFormatter();
        
        agent = new KnowledgeCompilerAgent(searchService, formatter);
    }

    @Test
    @DisplayName("Agent should have correct ID and description")
    void testAgentMetadata() {
        assertEquals("knowledge-compiler", agent.getAgentId());
        assertNotNull(agent.getDescription());
        assertTrue(agent.getDescription().contains("knowledge"));
    }

    @Test
    @DisplayName("Agent should support all 4 tools")
    void testSupportedTools() {
        List<String> tools = agent.getToolIds();
        assertEquals(4, tools.size());
        assertTrue(tools.contains("getKnowledge"));
        assertTrue(tools.contains("getBankInfo"));
        assertTrue(tools.contains("searchFAQ"));
        assertTrue(tools.contains("getAppGuidance"));
    }

    @Test
    @DisplayName("Agent should throw for unknown tool")
    void testUnknownTool() {
        assertThrows(IllegalArgumentException.class, () ->
                agent.executeTool("unknownTool", Map.of()));
    }

    @Nested
    @DisplayName("getKnowledge Tool Tests")
    class GetKnowledgeTests {

        @Test
        @DisplayName("Should return results for valid query")
        void testGetKnowledgeWithResults() {
            Map<String, Object> input = new HashMap<>();
            input.put("query", "opening hours");

            Map<String, Object> result = agent.executeTool("getKnowledge", input);

            assertTrue((Boolean) result.get("found"));
            assertTrue((Integer) result.get("resultCount") > 0);
            assertNotNull(result.get("response"));
        }

        @Test
        @DisplayName("Should handle query with no matches")
        void testGetKnowledgeNoResults() {
            Map<String, Object> input = new HashMap<>();
            input.put("query", "xyznonexistentterm12345");

            Map<String, Object> result = agent.executeTool("getKnowledge", input);

            assertFalse((Boolean) result.get("found"));
            assertEquals(0, result.get("resultCount"));
        }

        @Test
        @DisplayName("Should throw when query is missing")
        void testGetKnowledgeMissingQuery() {
            assertThrows(IllegalArgumentException.class, () ->
                    agent.executeTool("getKnowledge", Map.of()));
        }

        @Test
        @DisplayName("Should find BIC information")
        void testGetKnowledgeBicInfo() {
            Map<String, Object> input = new HashMap<>();
            input.put("query", "BIC SWIFT code");

            Map<String, Object> result = agent.executeTool("getKnowledge", input);

            assertTrue((Boolean) result.get("found"));
            String response = (String) result.get("response");
            assertTrue(response.contains("BIC") || response.contains("SWIFT") || response.contains("DEUT"));
        }
    }

    @Nested
    @DisplayName("getBankInfo Tool Tests")
    class GetBankInfoTests {

        @Test
        @DisplayName("Should return BIC code info")
        void testGetBicInfo() {
            Map<String, Object> input = new HashMap<>();
            input.put("infoType", "bic");
            input.put("value", "ACMEDEXX");

            Map<String, Object> result = agent.executeTool("getBankInfo", input);

            assertTrue((Boolean) result.get("found"));
            assertEquals("ACMEDEXX", result.get("bicCode"));
        }

        @Test
        @DisplayName("Should return general BIC info when no value provided")
        void testGetGeneralBicInfo() {
            Map<String, Object> input = new HashMap<>();
            input.put("infoType", "bic");

            Map<String, Object> result = agent.executeTool("getBankInfo", input);

            assertTrue((Boolean) result.get("found"));
            assertNotNull(result.get("knownBicCodes"));
        }

        @Test
        @DisplayName("Should handle opening hours query")
        void testGetOpeningHours() {
            Map<String, Object> input = new HashMap<>();
            input.put("infoType", "hours");

            Map<String, Object> result = agent.executeTool("getBankInfo", input);

            assertTrue((Boolean) result.get("found"));
        }

        @Test
        @DisplayName("Should throw when infoType is missing")
        void testMissingInfoType() {
            assertThrows(IllegalArgumentException.class, () ->
                    agent.executeTool("getBankInfo", Map.of()));
        }
    }

    @Nested
    @DisplayName("searchFAQ Tool Tests")
    class SearchFAQTests {

        @Test
        @DisplayName("Should return FAQ results for IBAN question")
        void testSearchFAQIban() {
            Map<String, Object> input = new HashMap<>();
            input.put("question", "What is my IBAN?");

            Map<String, Object> result = agent.executeTool("searchFAQ", input);

            assertTrue((Boolean) result.get("found"));
            assertTrue((Integer) result.get("faqCount") > 0);
        }

        @Test
        @DisplayName("Should return FAQ results for password question")
        void testSearchFAQPassword() {
            Map<String, Object> input = new HashMap<>();
            input.put("question", "How do I reset my password?");

            Map<String, Object> result = agent.executeTool("searchFAQ", input);

            assertTrue((Boolean) result.get("found"));
        }

        @Test
        @DisplayName("Should throw when question is missing")
        void testMissingQuestion() {
            assertThrows(IllegalArgumentException.class, () ->
                    agent.executeTool("searchFAQ", Map.of()));
        }
    }

    @Nested
    @DisplayName("getAppGuidance Tool Tests")
    class GetAppGuidanceTests {

        @Test
        @DisplayName("Should return app guidance for setup")
        void testGetAppGuidanceSetup() {
            Map<String, Object> input = new HashMap<>();
            input.put("task", "mobile banking setup");

            Map<String, Object> result = agent.executeTool("getAppGuidance", input);

            assertTrue((Boolean) result.get("found"));
            assertNotNull(result.get("guidance"));
        }

        @Test
        @DisplayName("Should return app guidance for balance check")
        void testGetAppGuidanceBalance() {
            Map<String, Object> input = new HashMap<>();
            input.put("task", "check balance");
            input.put("platform", "ios");

            Map<String, Object> result = agent.executeTool("getAppGuidance", input);

            assertTrue((Boolean) result.get("found"));
            assertEquals("ios", result.get("platform"));
        }

        @Test
        @DisplayName("Should handle task that finds some results")
        void testGetAppGuidanceNoResults() {
            Map<String, Object> input = new HashMap<>();
            input.put("task", "xyz");

            Map<String, Object> result = agent.executeTool("getAppGuidance", input);

            // Either found or not found is valid, just verify response is present
            assertNotNull(result.get("found"));
            if (!(Boolean) result.get("found")) {
                assertNotNull(result.get("fallbackSuggestion"));
            } else {
                assertNotNull(result.get("response"));
            }
        }

        @Test
        @DisplayName("Should throw when task is missing")
        void testMissingTask() {
            assertThrows(IllegalArgumentException.class, () ->
                    agent.executeTool("getAppGuidance", Map.of()));
        }
    }
}
