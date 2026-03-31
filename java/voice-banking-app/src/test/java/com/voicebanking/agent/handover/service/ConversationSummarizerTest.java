package com.voicebanking.agent.handover.service;

import com.voicebanking.agent.handover.domain.ConversationTurnContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ConversationSummarizer Tests")
class ConversationSummarizerTest {

    private ConversationSummarizer summarizer;

    @BeforeEach
    void setUp() {
        summarizer = new ConversationSummarizer();
    }

    @Test
    @DisplayName("Should return 'no history' message for empty list")
    void shouldReturnNoHistoryForEmptyList() {
        String summary = summarizer.summarize(Collections.emptyList());
        assertThat(summary).isEqualTo("No conversation history available.");
    }

    @Test
    @DisplayName("Should summarize single turn")
    void shouldSummarizeSingleTurn() {
        List<ConversationTurnContext> turns = List.of(
                new ConversationTurnContext(
                        "What is my balance?",
                        "Your account balance is €1,234.56",
                        "balance_inquiry",
                        "getBalance",
                        Map.of("accountType", "checking"),
                        Instant.now()
                )
        );

        String summary = summarizer.summarize(turns);
        assertThat(summary).isNotEmpty();
        assertThat(summary).contains("1 turns");
        assertThat(summary).contains("balance_inquiry");
        assertThat(summary).contains("getBalance");
        assertThat(summary).contains("What is my balance?");
    }

    @Test
    @DisplayName("Should summarize multiple turns with intents")
    void shouldSummarizeMultipleTurns() {
        List<ConversationTurnContext> turns = Arrays.asList(
                new ConversationTurnContext("Hello", "Welcome to voice banking", "greeting", null, Map.of(), Instant.now()),
                new ConversationTurnContext("Check balance", "Your balance is €500", "balance_inquiry", "getBalance", Map.of(), Instant.now()),
                new ConversationTurnContext("Transfer money", "I need more information", "transfer_funds", null, Map.of(), Instant.now())
        );

        String summary = summarizer.summarize(turns);
        assertThat(summary).contains("3 turns");
        assertThat(summary).contains("greeting");
        assertThat(summary).contains("balance_inquiry");
        assertThat(summary).contains("transfer_funds");
    }

    @Test
    @DisplayName("Should include tools used in summary")
    void shouldIncludeToolsUsed() {
        List<ConversationTurnContext> turns = List.of(
                new ConversationTurnContext("Check balance", "Your balance is €500", "balance_inquiry", "getBalance", Map.of(), Instant.now()),
                new ConversationTurnContext("Show transactions", "Here are your last transactions", "transaction_history", "getTransactions", Map.of(), Instant.now())
        );

        String summary = summarizer.summarize(turns);
        assertThat(summary).contains("getBalance");
        assertThat(summary).contains("getTransactions");
    }

    @Test
    @DisplayName("Should include last user request")
    void shouldIncludeLastUserRequest() {
        List<ConversationTurnContext> turns = Arrays.asList(
                new ConversationTurnContext("Hello", "Welcome", "greeting", null, Map.of(), Instant.now()),
                new ConversationTurnContext("I need help with a transfer", null, "transfer_funds", null, Map.of(), Instant.now())
        );

        String summary = summarizer.summarize(turns);
        assertThat(summary).contains("I need help with a transfer");
    }

    @Test
    @DisplayName("Should handle turns with null intents")
    void shouldHandleNullIntents() {
        List<ConversationTurnContext> turns = List.of(
                new ConversationTurnContext("mumble mumble", "Sorry, I didn't understand", null, null, Map.of(), Instant.now())
        );

        String summary = summarizer.summarize(turns);
        assertThat(summary).isNotEmpty();
        assertThat(summary).contains("mumble mumble");
    }

    @Test
    @DisplayName("Should handle turns with blank intents")
    void shouldHandleBlankIntents() {
        List<ConversationTurnContext> turns = List.of(
                new ConversationTurnContext("test input", "test response", "   ", null, Map.of(), Instant.now())
        );

        String summary = summarizer.summarize(turns);
        assertThat(summary).isNotEmpty();
        // Should not contain blank intent
        assertThat(summary).doesNotContain("Intents:    ");
    }

    @Test
    @DisplayName("Should deduplicate repeated intents")
    void shouldDeduplicateIntents() {
        List<ConversationTurnContext> turns = Arrays.asList(
                new ConversationTurnContext("Balance", "€100", "balance_inquiry", "getBalance", Map.of(), Instant.now()),
                new ConversationTurnContext("Balance again", "€100", "balance_inquiry", "getBalance", Map.of(), Instant.now()),
                new ConversationTurnContext("Balance one more time", "€100", "balance_inquiry", "getBalance", Map.of(), Instant.now())
        );

        String summary = summarizer.summarize(turns);
        // Should only mention balance_inquiry once
        int intentCount = countOccurrences(summary, "balance_inquiry");
        assertThat(intentCount).isEqualTo(1);
    }

    private int countOccurrences(String text, String search) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(search, index)) != -1) {
            count++;
            index += search.length();
        }
        return count;
    }
}
