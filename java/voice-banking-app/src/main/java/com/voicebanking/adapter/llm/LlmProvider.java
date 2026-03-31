package com.voicebanking.adapter.llm;

import java.util.Map;

/**
 * LLM Provider Interface
 * 
 * <p>Contract for Language Model providers. Implementations:</p>
 * <ul>
 *   <li>{@link LlmStubAdapter} - Deterministic stub for local/test</li>
 *   <li>LlmGeminiAdapter - Vertex AI Gemini (Phase 2)</li>
 * </ul>
 */
public interface LlmProvider {
    
    /**
     * Process transcript and determine intent/action
     * 
     * @param transcript Transcribed text from STT
     * @param context Optional context (e.g., session state, conversation history)
     * @return LLM response with intent classification and optional tool call
     */
    LlmResponse process(String transcript, Map<String, Object> context);
}
