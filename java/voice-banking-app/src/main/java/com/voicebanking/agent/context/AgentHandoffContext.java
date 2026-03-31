package com.voicebanking.agent.context;

import com.voicebanking.domain.dto.OrchestratorRequest;

import java.util.Map;
import java.util.UUID;

/**
 * Agent Handoff Context
 * 
 * Context passed when root agent hands off to a specialized agent.
 * Mirrors CES agent transfer protocol for consistent multi-agent orchestration.
 * 
 * <p>This implements the Root Agent Orchestration Pattern as described in ADR-BFA-006.
 * When the root orchestrator determines that a request should be handled by a
 * specialized agent, it creates an AgentHandoffContext containing all relevant
 * session and security information.</p>
 * 
 * <h3>Usage Pattern:</h3>
 * <pre>{@code
 * AgentHandoffContext handoff = AgentHandoffContext.from(request, "getSpendingBreakdown");
 * AgentExecutionResult result = agent.executeWithHandoff(toolId, input, handoff);
 * }</pre>
 * 
 * @see AgentReturnContext
 * @see com.voicebanking.agent.Agent#executeWithHandoff
 * @author Augment Agent
 * @since 2026-02-03
 */
public record AgentHandoffContext(
    /** User identifier */
    String userId,
    
    /** Conversation session ID */
    String sessionId,
    
    /** Request correlation ID for distributed tracing */
    String correlationId,
    
    /** Whether user has granted required consents */
    boolean consentGranted,
    
    /** Legitimation token (if already obtained) */
    String legitimationToken,
    
    /** The tool that triggered this handoff */
    String originalTool,
    
    /** Conversation context for multi-turn dialogue */
    Map<String, Object> conversationContext
) {
    
    /**
     * Create a handoff context from an OrchestratorRequest.
     * 
     * @param request the original request
     * @param toolName the tool that triggered the handoff
     * @return a new AgentHandoffContext
     */
    public static AgentHandoffContext from(OrchestratorRequest request, String toolName) {
        return new AgentHandoffContext(
            null,  // userId - to be extracted from authentication context
            request.sessionId(),
            UUID.randomUUID().toString(),
            request.consentAccepted(),
            null,  // Legitimation obtained on-demand
            toolName,
            Map.of()
        );
    }
    
    /**
     * Create a handoff context with explicit user ID.
     * 
     * @param userId the authenticated user ID
     * @param request the original request
     * @param toolName the tool that triggered the handoff
     * @return a new AgentHandoffContext
     */
    public static AgentHandoffContext withUserId(
            String userId, 
            OrchestratorRequest request, 
            String toolName) {
        return new AgentHandoffContext(
            userId,
            request.sessionId(),
            UUID.randomUUID().toString(),
            request.consentAccepted(),
            null,
            toolName,
            Map.of()
        );
    }
    
    /**
     * Create a copy of this context with a legitimation token.
     * Used after obtaining legitimation from a downstream service.
     * 
     * @param token the legitimation token
     * @return a new context with the token set
     */
    public AgentHandoffContext withLegitimation(String token) {
        return new AgentHandoffContext(
            userId,
            sessionId,
            correlationId,
            consentGranted,
            token,
            originalTool,
            conversationContext
        );
    }
    
    /**
     * Create a copy of this context with additional conversation context.
     * 
     * @param additionalContext context to merge
     * @return a new context with merged conversation context
     */
    public AgentHandoffContext withAdditionalContext(Map<String, Object> additionalContext) {
        Map<String, Object> merged = new java.util.HashMap<>(conversationContext);
        merged.putAll(additionalContext);
        return new AgentHandoffContext(
            userId,
            sessionId,
            correlationId,
            consentGranted,
            legitimationToken,
            originalTool,
            Map.copyOf(merged)
        );
    }
    
    /**
     * Check if legitimation has been obtained.
     * 
     * @return true if a legitimation token is present
     */
    public boolean hasLegitimation() {
        return legitimationToken != null && !legitimationToken.isBlank();
    }
}
