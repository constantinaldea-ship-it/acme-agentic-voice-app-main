package com.voicebanking.adapter.llm;

import com.google.adk.events.Event;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.BaseSessionService;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Flowable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ADK-based LLM Adapter
 * 
 * <p>Uses Google Agent Development Kit (ADK) for LLM processing with Gemini.
 * This replaces the raw Vertex AI SDK approach with ADK's agent framework.</p>
 * 
 * <p>Benefits:
 * <ul>
 *   <li>Declarative tool registration</li>
 *   <li>Built-in conversation history</li>
 *   <li>Automatic function calling dispatch</li>
 *   <li>Session management</li>
 * </ul>
 * </p>
 * 
 * <p>Active in 'cloud' profile only. This is the primary LLM provider.</p>
 */
@Component("llmAdkAdapter")
@Profile("cloud")
@Primary
@ConditionalOnProperty(name = "voice-banking.adk.enabled", havingValue = "true", matchIfMissing = true)
public class LlmAdkAdapter implements LlmProvider {

    private static final Logger log = LoggerFactory.getLogger(LlmAdkAdapter.class);
    private static final String APP_NAME = "voice-banking";
    
    private final Runner runner;
    private final BaseSessionService sessionService;
    
    // Cache for user sessions
    private final Map<String, Session> sessionCache = new ConcurrentHashMap<>();

    public LlmAdkAdapter(Runner runner, BaseSessionService sessionService) {
        this.runner = runner;
        this.sessionService = sessionService;
        log.info("LlmAdkAdapter initialized with ADK Runner");
    }

    @Override
    public LlmResponse process(String transcript, Map<String, Object> context) {
        String sessionId = (String) context.getOrDefault("sessionId", "default");
        
        log.info("ADK processing: sessionId={}, transcript='{}'", sessionId, transcript);
        long startTime = System.currentTimeMillis();
        
        try {
            // Get or create session
            Session session = getOrCreateSession(sessionId);
            
            // Create user message content
            Content userMessage = Content.fromParts(Part.fromText(transcript));
            
            // Run the agent and collect events (blocking)
            Flowable<Event> eventFlowable = runner.runAsync(
                session.userId(),
                session.id(),
                userMessage
            );
            
            // Collect all events into a list (blocking)
            List<Event> events = eventFlowable.toList().blockingGet();
            
            // Process events and build response
            LlmResponse response = processEvents(events);
            
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("ADK processing completed in {}ms: type={}", durationMs, response.type());
            
            return response;
            
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startTime;
            log.error("ADK processing failed after {}ms", durationMs, e);
            return LlmResponse.refusal("I encountered an error processing your request. Please try again.");
        }
    }

    /**
     * Get or create an ADK session for the given session ID.
     */
    private Session getOrCreateSession(String sessionId) {
        Session cached = sessionCache.get(sessionId);
        if (cached != null) {
            return cached;
        }
        
        log.debug("Creating new ADK session: {}", sessionId);
        Session newSession = sessionService.createSession(APP_NAME, sessionId).blockingGet();
        sessionCache.put(sessionId, newSession);
        return newSession;
    }

    /**
     * Process ADK events and build LlmResponse.
     */
    private LlmResponse processEvents(List<Event> events) {
        StringBuilder responseText = new StringBuilder();
        ToolCall toolCall = null;
        String toolName = null;
        Object toolResult = null;
        
        for (Event event : events) {
            log.debug("ADK Event: type={}", event.getClass().getSimpleName());
            
            // Check event type and extract relevant information
            Optional<Content> contentOpt = event.content();
            if (contentOpt.isPresent()) {
                Content content = contentOpt.get();
                Optional<List<Part>> partsOpt = content.parts();
                if (partsOpt.isPresent()) {
                    for (Part part : partsOpt.get()) {
                        // Text response
                        Optional<String> textOpt = part.text();
                        if (textOpt.isPresent() && !textOpt.get().isEmpty()) {
                            responseText.append(textOpt.get());
                        }
                        
                        // Function call
                        Optional<com.google.genai.types.FunctionCall> fcOpt = part.functionCall();
                        if (fcOpt.isPresent()) {
                            var fc = fcOpt.get();
                            toolName = fc.name().orElse("unknown");
                            Map<String, Object> args = fc.args().orElse(Map.of());
                            toolCall = new ToolCall(toolName, new java.util.HashMap<>(args));
                            log.info("ADK detected tool call: {}", toolName);
                        }
                        
                        // Function response (tool result)
                        Optional<com.google.genai.types.FunctionResponse> frOpt = part.functionResponse();
                        if (frOpt.isPresent()) {
                            var fr = frOpt.get();
                            toolResult = fr.response();
                            log.info("ADK tool result received for: {}", fr.name().orElse("unknown"));
                        }
                    }
                }
            }
        }
        
        // Build appropriate response type
        if (toolCall != null) {
            // Tool was called - ADK handles execution automatically
            String message = responseText.length() > 0 ? 
                responseText.toString() : 
                "I'll " + getToolDescription(toolCall.toolName()) + " for you.";
            return LlmResponse.toolCall(toolCall, message);
        }
        
        if (responseText.length() > 0) {
            String text = responseText.toString().trim();
            
            // Check for refusal patterns
            if (isRefusal(text)) {
                return LlmResponse.refusal(text);
            }
            
            // Check for clarification patterns
            if (isClarification(text)) {
                return LlmResponse.clarification(text);
            }
            
            return new LlmResponse(LlmResponse.ResponseType.DIRECT_RESPONSE, null, text, null);
        }
        
        // Fallback
        return LlmResponse.clarification("I'm not sure what you'd like to do. Could you please rephrase?");
    }

    private boolean isRefusal(String text) {
        String lower = text.toLowerCase();
        return lower.contains("cannot perform") || 
               lower.contains("can't help with that") ||
               lower.contains("not able to") ||
               lower.contains("unable to process") ||
               lower.contains("don't have the ability");
    }

    private boolean isClarification(String text) {
        String lower = text.toLowerCase();
        return lower.contains("could you clarify") ||
               lower.contains("please specify") ||
               lower.contains("which account") ||
               lower.contains("what would you like");
    }

    private String getToolDescription(String toolName) {
        return switch (toolName) {
            case "getBalance" -> "check your balance";
            case "listAccounts" -> "list your accounts";
            case "queryTransactions" -> "search your transactions";
            default -> "help you with that";
        };
    }
}
