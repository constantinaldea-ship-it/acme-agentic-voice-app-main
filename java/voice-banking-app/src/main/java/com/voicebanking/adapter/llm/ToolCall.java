package com.voicebanking.adapter.llm;

import java.util.Map;

/**
 * Tool Call
 * 
 * <p>Represents a tool invocation request from the LLM.</p>
 * 
 * @param toolName Name of the tool to invoke
 * @param input Tool input parameters (JSON-compatible map)
 */
public record ToolCall(
    String toolName,
    Map<String, Object> input
) {
}
