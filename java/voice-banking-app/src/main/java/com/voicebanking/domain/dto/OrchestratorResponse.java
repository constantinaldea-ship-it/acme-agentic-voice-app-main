package com.voicebanking.domain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Orchestrator Response DTO
 * 
 * <p>Output from the orchestrator after processing a request.</p>
 * 
 * @param transcript Transcribed text from STT (or original text if provided)
 * @param intent Detected user intent (e.g., "balance_inquiry", "list_accounts")
 * @param toolCalled Name of tool that was executed (null if refusal)
 * @param toolResult Result from tool execution (JSON-compatible object)
 * @param responseText Human-readable response text
 * @param refusalReason Reason for refusal (null if request was processed)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrchestratorResponse(
    String transcript,
    String intent,
    String toolCalled,
    Object toolResult,
    String responseText,
    String refusalReason
) {
    /**
     * Create a successful response
     */
    public static OrchestratorResponse success(
            String transcript,
            String intent,
            String toolCalled,
            Object toolResult,
            String responseText) {
        return new OrchestratorResponse(
            transcript,
            intent,
            toolCalled,
            toolResult,
            responseText,
            null
        );
    }
    
    /**
     * Create a refusal response
     */
    public static OrchestratorResponse refusal(
            String transcript,
            String intent,
            String refusalReason) {
        return new OrchestratorResponse(
            transcript,
            intent,
            null,
            null,
            "I'm sorry, but " + refusalReason,
            refusalReason
        );
    }
}
