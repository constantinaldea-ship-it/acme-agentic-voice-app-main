package com.voicebanking.agent.knowledge;

import com.voicebanking.agent.Agent;
import com.voicebanking.agent.knowledge.domain.*;
import com.voicebanking.agent.knowledge.domain.KnowledgeQuery.OutputFormat;
import com.voicebanking.agent.knowledge.service.KnowledgeFormatter;
import com.voicebanking.agent.knowledge.service.SemanticSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Knowledge Compiler Agent
 * 
 * Functional agent responsible for knowledge retrieval and FAQ handling:
 * - General knowledge queries (getKnowledge)
 * - Bank information lookup (getBankInfo)
 * - FAQ search (searchFAQ)
 * - Mobile app guidance (getAppGuidance)
 * 
 * Architecture Reference: Component E (AI Functional Agents) - Knowledge domain
 * Implementation Plan: AGENT-003-knowledge-compiler.md
 * 
 * @author Augment Agent
 * @since 2026-01-23
 */
@Component
public class KnowledgeCompilerAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeCompilerAgent.class);

    private static final String AGENT_ID = "knowledge-compiler";
    private static final List<String> TOOL_IDS = List.of(
            "getKnowledge",
            "getBankInfo",
            "searchFAQ",
            "getAppGuidance"
    );

    private final SemanticSearchService searchService;
    private final KnowledgeFormatter formatter;

    public KnowledgeCompilerAgent(SemanticSearchService searchService, KnowledgeFormatter formatter) {
        this.searchService = searchService;
        this.formatter = formatter;
        log.info("KnowledgeCompilerAgent initialized with {} tools", TOOL_IDS.size());
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    @Override
    public String getDescription() {
        return "Handles knowledge retrieval, FAQ searches, bank information lookup, and mobile app guidance";
    }

    @Override
    public List<String> getToolIds() {
        return TOOL_IDS;
    }

    @Override
    public Map<String, Object> executeTool(String toolId, Map<String, Object> input) {
        log.debug("KnowledgeCompilerAgent executing tool: {} with input: {}", toolId, input);

        return switch (toolId) {
            case "getKnowledge" -> executeGetKnowledge(input);
            case "getBankInfo" -> executeGetBankInfo(input);
            case "searchFAQ" -> executeSearchFAQ(input);
            case "getAppGuidance" -> executeGetAppGuidance(input);
            default -> throw new IllegalArgumentException("Unknown tool: " + toolId);
        };
    }

    /**
     * Execute getKnowledge tool - general knowledge search.
     * Input: query (required), category (optional), maxResults (optional), format (optional)
     */
    private Map<String, Object> executeGetKnowledge(Map<String, Object> input) {
        String queryText = getRequiredString(input, "query");
        KnowledgeCategory category = parseCategory(input.get("category"));
        int maxResults = parseIntOrDefault(input.get("maxResults"), 5);
        OutputFormat format = parseOutputFormat(input.get("format"));

        KnowledgeQuery query = new KnowledgeQuery(queryText, category, maxResults, 0.3, format);
        List<SearchResult> results = searchService.search(query);

        Map<String, Object> response = new HashMap<>();
        if (results.isEmpty()) {
            response.put("found", false);
            response.put("response", formatter.formatNoResults(queryText));
            response.put("resultCount", 0);
        } else {
            response.put("found", true);
            response.put("response", formatter.formatMultipleResults(results, format));
            response.put("resultCount", results.size());
            response.put("topResult", formatResultForOutput(results.get(0), format));
            
            if (results.size() > 1) {
                response.put("hasMoreResults", true);
                response.put("additionalTopics", results.stream()
                        .skip(1)
                        .map(r -> r.article().title())
                        .toList());
            }
        }

        log.debug("getKnowledge for '{}' returned {} results", queryText, results.size());
        return response;
    }

    /**
     * Execute getBankInfo tool - bank-specific information lookup (BIC, hours, contact).
     * Input: infoType (required), value (optional for BIC lookup)
     */
    private Map<String, Object> executeGetBankInfo(Map<String, Object> input) {
        String infoType = getRequiredString(input, "infoType").toLowerCase();
        String value = (String) input.get("value");
        OutputFormat format = parseOutputFormat(input.get("format"));

        Map<String, Object> response = new HashMap<>();

        return switch (infoType) {
            case "bic", "swift" -> handleBicLookup(value, format, response);
            case "hours", "opening_hours" -> handleInfoQuery("opening hours Acme Bank", format, response);
            case "contact", "customer_service" -> handleInfoQuery("customer service contact", format, response);
            case "branch" -> handleBranchInfo(value, format, response);
            default -> handleInfoQuery(infoType, format, response);
        };
    }

    private Map<String, Object> handleBicLookup(String bicCode, OutputFormat format, Map<String, Object> response) {
        if (bicCode != null && !bicCode.isBlank()) {
            Optional<SearchResult> result = searchService.searchBicCode(bicCode);
            if (result.isPresent()) {
                response.put("found", true);
                response.put("bicCode", bicCode.toUpperCase());
                response.put("valid", true);
                response.put("response", formatter.formatBicResponse(bicCode.toUpperCase(), true));
                response.put("details", formatResultForOutput(result.get(), format));
            } else {
                response.put("found", false);
                response.put("bicCode", bicCode.toUpperCase());
                response.put("valid", false);
                response.put("response", formatter.formatBicResponse(bicCode.toUpperCase(), false));
            }
        } else {
            // General BIC info query
            KnowledgeQuery query = KnowledgeQuery.withDefaults("BIC SWIFT code Acme Bank");
            List<SearchResult> results = searchService.search(query);
            if (!results.isEmpty()) {
                response.put("found", true);
                response.put("response", formatter.formatMultipleResults(results, format));
                response.put("knownBicCodes", List.of("ACMEDEXX", "ACMEDEFF"));
            } else {
                response.put("found", false);
                response.put("response", "Acme Bank's primary BIC code is ACMEDEXX. The SWIFT code is ACMEDEFF.");
            }
        }
        return response;
    }

    private Map<String, Object> handleInfoQuery(String topic, OutputFormat format, Map<String, Object> response) {
        KnowledgeQuery query = KnowledgeQuery.forCategory(topic, KnowledgeCategory.GENERAL_INFO);
        List<SearchResult> results = searchService.search(query);
        
        if (!results.isEmpty()) {
            response.put("found", true);
            response.put("response", formatter.formatMultipleResults(results, format));
            response.put("topResult", formatResultForOutput(results.get(0), format));
        } else {
            response.put("found", false);
            response.put("response", formatter.formatNoResults(topic));
        }
        return response;
    }

    private Map<String, Object> handleBranchInfo(String branchName, OutputFormat format, Map<String, Object> response) {
        String searchTerm = branchName != null ? branchName + " branch" : "branch";
        KnowledgeQuery query = KnowledgeQuery.forCategory(searchTerm, KnowledgeCategory.BRANCH_INFO);
        List<SearchResult> results = searchService.search(query);
        
        if (!results.isEmpty()) {
            response.put("found", true);
            response.put("response", formatter.formatMultipleResults(results, format));
            response.put("branchDetails", formatResultForOutput(results.get(0), format));
        } else {
            response.put("found", false);
            response.put("response", "I couldn't find information about that branch. You can use our branch locator on the website or mobile app.");
        }
        return response;
    }

    /**
     * Execute searchFAQ tool - search frequently asked questions.
     * Input: question (required), maxResults (optional)
     */
    private Map<String, Object> executeSearchFAQ(Map<String, Object> input) {
        String question = getRequiredString(input, "question");
        int maxResults = parseIntOrDefault(input.get("maxResults"), 5);
        OutputFormat format = parseOutputFormat(input.get("format"));

        List<SearchResult> results = searchService.searchFaq(question);

        Map<String, Object> response = new HashMap<>();
        if (results.isEmpty()) {
            // Fall back to general search if no FAQ matches
            KnowledgeQuery query = KnowledgeQuery.withDefaults(question);
            results = searchService.search(query);
        }

        if (results.isEmpty()) {
            response.put("found", false);
            response.put("response", formatter.formatNoResults(question));
            response.put("suggestion", "You might want to contact customer service for help with this question.");
        } else {
            response.put("found", true);
            response.put("response", formatter.formatMultipleResults(results, format));
            response.put("faqCount", results.size());
            response.put("topics", formatter.formatFaqTopics(results));
            
            if (!results.isEmpty()) {
                response.put("topAnswer", formatResultForOutput(results.get(0), format));
            }
        }

        log.debug("searchFAQ for '{}' returned {} results", question, results.size());
        return response;
    }

    /**
     * Execute getAppGuidance tool - mobile app usage guidance.
     * Input: task (required), platform (optional)
     */
    private Map<String, Object> executeGetAppGuidance(Map<String, Object> input) {
        String task = getRequiredString(input, "task");
        String platform = (String) input.getOrDefault("platform", "general");
        OutputFormat format = parseOutputFormat(input.get("format"));

        String searchTerm = task + " mobile app " + platform;
        List<SearchResult> results = searchService.searchAppGuidance(searchTerm);

        Map<String, Object> response = new HashMap<>();
        if (results.isEmpty()) {
            // Broader search
            KnowledgeQuery query = KnowledgeQuery.withDefaults(task + " app");
            results = searchService.search(query);
        }

        if (results.isEmpty()) {
            response.put("found", false);
            response.put("response", "I couldn't find specific app guidance for that task. " +
                    "You can find help in the app's menu under 'Help & Support'.");
            response.put("fallbackSuggestion", "Check the Help section in the Acme Bank Mobile app");
        } else {
            response.put("found", true);
            response.put("response", formatter.formatMultipleResults(results, format));
            response.put("platform", platform);
            
            FormattedKnowledge formatted = formatter.format(results.get(0), format);
            if (formatted != null) {
                response.put("guidance", formatted.voiceResponse());
                response.put("hasMoreSteps", formatted.hasMoreDetail());
            }
        }

        log.debug("getAppGuidance for '{}' (platform: {}) returned {} results", task, platform, results.size());
        return response;
    }

    // ========== Helper Methods ==========

    private String getRequiredString(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Required parameter missing: " + key);
        }
        return value.toString();
    }

    private KnowledgeCategory parseCategory(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return KnowledgeCategory.valueOf(value.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid category: {}", value);
            return null;
        }
    }

    private int parseIntOrDefault(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private OutputFormat parseOutputFormat(Object value) {
        if (value == null) {
            return OutputFormat.VOICE;
        }
        try {
            return OutputFormat.valueOf(value.toString().toUpperCase());
        } catch (IllegalArgumentException e) {
            return OutputFormat.VOICE;
        }
    }

    private Map<String, Object> formatResultForOutput(SearchResult result, OutputFormat format) {
        FormattedKnowledge formatted = formatter.format(result, format);
        if (formatted == null) {
            return Map.of();
        }

        Map<String, Object> output = new HashMap<>();
        output.put("articleId", formatted.articleId());
        output.put("category", formatted.category().name());
        output.put("voiceResponse", formatted.voiceResponse());
        output.put("textResponse", formatted.textResponse());
        output.put("hasMoreDetail", formatted.hasMoreDetail());
        output.put("confidence", result.score());
        output.put("matchType", result.matchType().name());
        return output;
    }
}
