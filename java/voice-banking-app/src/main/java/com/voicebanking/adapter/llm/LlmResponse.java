package com.voicebanking.adapter.llm;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * LLM Response
 * 
 * <p>Output from LLM provider after processing a transcript.</p>
 * 
 * @param type Response type (TOOL_CALL, CLARIFICATION, REFUSAL, DIRECT_RESPONSE)
 * @param toolCall Tool invocation request (null unless type=TOOL_CALL)
 * @param message Human-readable message
 * @param confidence Confidence score (0.0-1.0), null if not available
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmResponse(
    ResponseType type,
    ToolCall toolCall,
    String message,
    Double confidence
) {
    public enum ResponseType {
        TOOL_CALL,
        CLARIFICATION,
        REFUSAL,
        DIRECT_RESPONSE
    }
    
    /**
     * Create a tool call response
     */
    public static LlmResponse toolCall(ToolCall toolCall, String message) {
        return new LlmResponse(ResponseType.TOOL_CALL, toolCall, message, null);
    }
    
    /**
     * Create a refusal response
     */
    public static LlmResponse refusal(String message) {
        return new LlmResponse(ResponseType.REFUSAL, null, message, null);
    }
    
    /**
     * Create a clarification response
     */
    public static LlmResponse clarification(String message) {
        return new LlmResponse(ResponseType.CLARIFICATION, null, message, null);
    }
}
