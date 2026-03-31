package com.voicebanking.agent.knowledge.service;

import com.voicebanking.agent.knowledge.domain.*;
import com.voicebanking.agent.knowledge.domain.KnowledgeQuery.OutputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Formats knowledge articles for voice and text output.
 * Optimizes responses for voice delivery with concise, natural language.
 */
@Service
public class KnowledgeFormatter {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeFormatter.class);
    private static final int MAX_VOICE_WORDS = 75;
    private static final int SUMMARY_WORD_LIMIT = 50;

    /**
     * Format a single search result for output.
     */
    public FormattedKnowledge format(SearchResult result, OutputFormat outputFormat) {
        if (result == null || result.article() == null) {
            return null;
        }

        KnowledgeArticle article = result.article();
        boolean isVoice = outputFormat == OutputFormat.VOICE;

        String voiceResponse = formatForVoice(article, result.isHighConfidence());
        String textResponse = formatForText(article);
        boolean hasMoreDetail = article.content().split("\\s+").length > MAX_VOICE_WORDS;

        return new FormattedKnowledge(
                article.articleId(),
                voiceResponse,
                textResponse,
                hasMoreDetail,
                article.category(),
                voiceResponse.split("\\s+").length
        );
    }

    /**
     * Format multiple search results for voice output.
     */
    public String formatMultipleResults(List<SearchResult> results, OutputFormat outputFormat) {
        if (results == null || results.isEmpty()) {
            return "I couldn't find any information on that topic. Would you like me to search for something else?";
        }

        if (results.size() == 1) {
            FormattedKnowledge formatted = format(results.get(0), outputFormat);
            return formatted != null ? formatted.voiceResponse() : "I found some information but couldn't format it properly.";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("I found ").append(results.size()).append(" relevant topics. ");

        // For voice, summarize top 3
        int limit = Math.min(results.size(), 3);
        for (int i = 0; i < limit; i++) {
            SearchResult result = results.get(i);
            if (i > 0) {
                sb.append(" ");
            }
            sb.append(formatBriefSummary(result.article(), i + 1));
        }

        if (results.size() > 3) {
            sb.append(" Would you like to hear about any of these in more detail?");
        }

        return sb.toString();
    }

    private String formatForVoice(KnowledgeArticle article, boolean highConfidence) {
        // Use summary for voice output, optimized for listening
        String summary = article.summary();

        // Add category context for some categories
        String categoryPrefix = getCategoryPrefix(article.category());

        // Build natural voice response
        StringBuilder response = new StringBuilder();
        if (!categoryPrefix.isEmpty()) {
            response.append(categoryPrefix).append(" ");
        }

        if (highConfidence) {
            response.append(summary);
        } else {
            response.append("Based on what I found, ").append(summary.substring(0, 1).toLowerCase())
                    .append(summary.substring(1));
        }

        // Truncate if too long
        String voiceText = response.toString();
        if (voiceText.split("\\s+").length > MAX_VOICE_WORDS) {
            voiceText = truncateToWords(voiceText, MAX_VOICE_WORDS) + "... Would you like me to continue?";
        }

        return voiceText;
    }

    private String formatForText(KnowledgeArticle article) {
        // For text output, provide more detail
        StringBuilder text = new StringBuilder();
        text.append("**").append(article.title()).append("**\n\n");
        text.append(article.content()).append("\n\n");

        if (!article.keywords().isEmpty()) {
            text.append("_Related: ").append(String.join(", ", article.keywords())).append("_");
        }

        return text.toString();
    }

    private String formatBriefSummary(KnowledgeArticle article, int index) {
        String title = article.title();
        // Simplify long titles
        if (title.length() > 40) {
            title = title.substring(0, 37) + "...";
        }
        return String.format("Number %d: %s.", index, title);
    }

    private String getCategoryPrefix(KnowledgeCategory category) {
        return switch (category) {
            case GENERAL_INFO -> "";
            case PRODUCT_INFO -> "Regarding our products:";
            case HOW_TO -> "Here's how to do that:";
            case FAQ -> "";
            case BRANCH_INFO -> "About our branch:";
            case APP_GUIDANCE -> "In the mobile app:";
        };
    }

    private String truncateToWords(String text, int maxWords) {
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) {
            return text;
        }

        StringBuilder truncated = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) {
                truncated.append(" ");
            }
            truncated.append(words[i]);
        }
        return truncated.toString();
    }

    /**
     * Format a specific BIC code response.
     */
    public String formatBicResponse(String bicCode, boolean found) {
        if (found) {
            return String.format("The BIC code %s is valid for Acme Bank. " +
                    "You can use this code for international transfers to your Acme Bank account.", bicCode);
        } else {
            return String.format("I couldn't verify the BIC code %s. " +
                    "Acme Bank's main BIC codes are ACMEDEXX and ACMEDEFF. " +
                    "Would you like me to look up more information?", bicCode);
        }
    }

    /**
     * Format a no-results response.
     */
    public String formatNoResults(String query) {
        return String.format("I couldn't find specific information about '%s'. " +
                "You might want to try rephrasing your question, or I can connect you with a customer service representative.",
                truncateToWords(query, 10));
    }

    /**
     * Format a list of FAQ topics.
     */
    public String formatFaqTopics(List<SearchResult> results) {
        if (results.isEmpty()) {
            return "I don't have any FAQ topics matching your question. What else would you like to know about?";
        }

        String topics = results.stream()
                .limit(5)
                .map(r -> r.article().title())
                .collect(Collectors.joining(", "));

        return "Here are some related questions I can help with: " + topics + ". Which one would you like to know more about?";
    }
}
