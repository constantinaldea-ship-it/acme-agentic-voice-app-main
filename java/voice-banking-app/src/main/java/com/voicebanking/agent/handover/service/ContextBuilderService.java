package com.voicebanking.agent.handover.service;

import com.voicebanking.agent.handover.domain.ConversationTurnContext;
import com.voicebanking.agent.handover.domain.HandoverContext;
import com.voicebanking.agent.handover.domain.HandoverReason;
import com.voicebanking.session.AdkSessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ContextBuilderService {
    private static final Logger log = LoggerFactory.getLogger(ContextBuilderService.class);
    private static final int MAX_HISTORY_TURNS = 10;

    private final AdkSessionManager sessionManager;
    private final ConversationSummarizer summarizer;

    public ContextBuilderService(AdkSessionManager sessionManager, ConversationSummarizer summarizer) {
        this.sessionManager = sessionManager;
        this.summarizer = summarizer;
    }

    public HandoverContext buildContext(String sessionId, HandoverReason reason) {
        return buildContext(sessionId, reason, null);
    }

    public HandoverContext buildContext(String sessionId, HandoverReason reason, String policyCategory) {
        log.info("Building handover context for session: {}, reason: {}", sessionId, reason);

        var sessionContext = sessionManager.getOrCreateSession(sessionId);
        var history = sessionManager.getSessionHistory(sessionId);

        // Extract information from session history
        List<ConversationTurnContext> turns = extractTurns(history);
        List<String> toolsCalled = extractToolsCalled(history);
        String detectedIntent = extractLastIntent(history);
        Map<String, Object> entities = extractEntities(history);

        // Generate summary
        String summary = summarizer.summarize(turns);

        // Get customer ID from preferences
        String customerId = (String) sessionManager.getPreference(sessionId, "customerId");

        return HandoverContext.builder()
            .sessionId(sessionId)
            .customerId(customerId)
            .conversationSummary(summary)
            .detectedIntent(detectedIntent)
            .entities(entities)
            .toolsCalled(toolsCalled)
            .handoverReason(reason)
            .policyCategory(policyCategory)
            .conversationHistory(turns)
            .startedAt(sessionContext.getCreatedAt())
            .handoverAt(Instant.now())
            .build();
    }

    private List<ConversationTurnContext> extractTurns(List<AdkSessionManager.ConversationTurn> history) {
        int startIdx = Math.max(0, history.size() - MAX_HISTORY_TURNS);
        return history.subList(startIdx, history.size()).stream()
            .map(t -> new ConversationTurnContext(
                t.transcript(), null, t.intent(), t.toolCalled(), Map.of(), t.timestamp()
            ))
            .collect(Collectors.toList());
    }

    private List<String> extractToolsCalled(List<AdkSessionManager.ConversationTurn> history) {
        return history.stream()
            .map(AdkSessionManager.ConversationTurn::toolCalled)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
    }

    private String extractLastIntent(List<AdkSessionManager.ConversationTurn> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            String intent = history.get(i).intent();
            if (intent != null && !intent.isBlank()) {
                return intent;
            }
        }
        return "UNKNOWN";
    }

    private Map<String, Object> extractEntities(List<AdkSessionManager.ConversationTurn> history) {
        // Aggregate entities from tool results
        Map<String, Object> entities = new HashMap<>();
        for (var turn : history) {
            if (turn.toolResult() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> result = (Map<String, Object>) turn.toolResult();
                if (result.containsKey("accountId")) entities.put("accountId", result.get("accountId"));
                if (result.containsKey("amount")) entities.put("lastAmount", result.get("amount"));
            }
        }
        return entities;
    }
}
