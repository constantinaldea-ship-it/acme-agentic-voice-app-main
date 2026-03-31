package com.voicebanking.agent.handover.domain;

import java.time.Instant;
import java.util.Map;

public record ConversationTurnContext(
    String userInput,
    String assistantResponse,
    String intent,
    String toolCalled,
    Map<String, Object> entities,
    Instant timestamp
) {
    public ConversationTurnContext(String userInput, String assistantResponse, 
                                   String intent, String toolCalled, 
                                   Map<String, Object> entities) {
        this(userInput, assistantResponse, intent, toolCalled, entities, Instant.now());
    }
    
    public static ConversationTurnContext userOnly(String userInput) {
        return new ConversationTurnContext(userInput, null, null, null, Map.of(), Instant.now());
    }
    
    public String formatForAgent() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(timestamp).append("] Customer: ").append(userInput);
        if (intent != null) sb.append(" (Intent: ").append(intent).append(")");
        if (assistantResponse != null) sb.append(" -> AI: ").append(assistantResponse);
        if (toolCalled != null) sb.append(" [Tool: ").append(toolCalled).append("]");
        return sb.toString();
    }
}
