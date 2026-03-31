package com.voicebanking.agent.handover.service;

import com.voicebanking.agent.handover.domain.ConversationTurnContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ConversationSummarizer {
    private static final int MAX_SUMMARY_WORDS = 200;

    public String summarize(List<ConversationTurnContext> turns) {
        if (turns.isEmpty()) {
            return "No conversation history available.";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("Customer conversation summary (").append(turns.size()).append(" turns): ");

        // Extract intents
        List<String> intents = turns.stream()
            .map(ConversationTurnContext::intent)
            .filter(i -> i != null && !i.isBlank())
            .distinct()
            .collect(Collectors.toList());

        if (!intents.isEmpty()) {
            summary.append("Intents: ").append(String.join(", ", intents)).append(". ");
        }

        // Extract tools used
        List<String> tools = turns.stream()
            .map(ConversationTurnContext::toolCalled)
            .filter(t -> t != null && !t.isBlank())
            .distinct()
            .collect(Collectors.toList());

        if (!tools.isEmpty()) {
            summary.append("Actions taken: ").append(String.join(", ", tools)).append(". ");
        }

        // Add last user request
        String lastInput = turns.get(turns.size() - 1).userInput();
        if (lastInput != null) {
            summary.append("Last request: ").append(lastInput);
        }

        return truncateToWords(summary.toString(), MAX_SUMMARY_WORDS);
    }

    private String truncateToWords(String text, int maxWords) {
        String[] words = text.split("\\s+");
        if (words.length <= maxWords) return text;
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) result.append(" ");
            result.append(words[i]);
        }
        result.append("...");
        return result.toString();
    }
}
