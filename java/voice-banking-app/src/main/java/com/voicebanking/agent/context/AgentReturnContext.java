package com.voicebanking.agent.context;

import java.util.Map;

/**
 * Agent Return Context
 * 
 * Context returned when a specialized agent completes and returns to root.
 * Mirrors CES agent return protocol for consistent multi-agent orchestration.
 * 
 * <p>This implements the Root Agent Orchestration Pattern as described in ADR-BFA-006.
 * When a specialized agent finishes processing, it returns an AgentReturnContext
 * to the root orchestrator, which then determines how to continue the conversation.</p>
 * 
 * <h3>Return Reasons:</h3>
 * <ul>
 *   <li>{@link ReturnReason#COMPLETED} - Agent finished successfully</li>
 *   <li>{@link ReturnReason#NEEDS_HANDOVER} - Agent needs human escalation</li>
 *   <li>{@link ReturnReason#OUT_OF_SCOPE} - Request outside agent's domain</li>
 *   <li>{@link ReturnReason#ERROR} - Agent encountered an error</li>
 *   <li>{@link ReturnReason#USER_CANCELLED} - User requested to go back</li>
 * </ul>
 * 
 * @see AgentHandoffContext
 * @see com.voicebanking.agent.Agent#executeWithHandoff
 * @author Augment Agent
 * @since 2026-02-03
 */
public record AgentReturnContext(
    /** Why the agent is returning */
    ReturnReason returnReason,
    
    /** Target intent if routing to another flow (for OUT_OF_SCOPE) */
    String targetIntent,
    
    /** Result data from the agent */
    Map<String, Object> resultData,
    
    /** Updated conversation context to pass back */
    Map<String, Object> conversationContext
) {
    
    /**
     * Reasons why an agent returns control to the root orchestrator.
     */
    public enum ReturnReason {
        /** Agent completed successfully */
        COMPLETED,
        
        /** Agent needs human handover */
        NEEDS_HANDOVER,
        
        /** Request is out of agent's scope */
        OUT_OF_SCOPE,
        
        /** Agent encountered an error */
        ERROR,
        
        /** User requested to go back */
        USER_CANCELLED
    }
    
    /**
     * Create a successful completion return context.
     * 
     * @param resultData the result data
     * @return a new AgentReturnContext
     */
    public static AgentReturnContext completed(Map<String, Object> resultData) {
        return new AgentReturnContext(
            ReturnReason.COMPLETED,
            null,
            resultData,
            Map.of()
        );
    }
    
    /**
     * Create a completion return context with conversation context.
     * 
     * @param resultData the result data
     * @param conversationContext updated conversation context
     * @return a new AgentReturnContext
     */
    public static AgentReturnContext completed(
            Map<String, Object> resultData, 
            Map<String, Object> conversationContext) {
        return new AgentReturnContext(
            ReturnReason.COMPLETED,
            null,
            resultData,
            conversationContext
        );
    }
    
    /**
     * Create a human handover return context.
     * 
     * @param reason the reason for handover
     * @return a new AgentReturnContext
     */
    public static AgentReturnContext needsHandover(String reason) {
        return new AgentReturnContext(
            ReturnReason.NEEDS_HANDOVER,
            null,
            Map.of("handoverReason", reason),
            Map.of()
        );
    }
    
    /**
     * Create an out-of-scope return context.
     * 
     * @param targetIntent the suggested intent to route to
     * @return a new AgentReturnContext
     */
    public static AgentReturnContext outOfScope(String targetIntent) {
        return new AgentReturnContext(
            ReturnReason.OUT_OF_SCOPE,
            targetIntent,
            Map.of(),
            Map.of()
        );
    }
    
    /**
     * Create an error return context.
     * 
     * @param errorMessage the error message
     * @return a new AgentReturnContext
     */
    public static AgentReturnContext error(String errorMessage) {
        return new AgentReturnContext(
            ReturnReason.ERROR,
            null,
            Map.of("error", errorMessage),
            Map.of()
        );
    }
    
    /**
     * Create a user-cancelled return context.
     * 
     * @return a new AgentReturnContext
     */
    public static AgentReturnContext userCancelled() {
        return new AgentReturnContext(
            ReturnReason.USER_CANCELLED,
            null,
            Map.of(),
            Map.of()
        );
    }
    
    /**
     * Check if this return context indicates success.
     * 
     * @return true if the return reason is COMPLETED
     */
    public boolean isSuccess() {
        return returnReason == ReturnReason.COMPLETED;
    }
    
    /**
     * Check if this return context indicates an error.
     * 
     * @return true if the return reason is ERROR
     */
    public boolean isError() {
        return returnReason == ReturnReason.ERROR;
    }
    
    /**
     * Check if this return context requires human intervention.
     * 
     * @return true if the return reason is NEEDS_HANDOVER
     */
    public boolean needsHumanIntervention() {
        return returnReason == ReturnReason.NEEDS_HANDOVER;
    }
}
