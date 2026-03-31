package com.voicebanking.agent.context;

import java.util.Map;

/**
 * Agent Execution Result
 * 
 * Wrapper for the result of an agent's tool execution that includes
 * both the result data and the return context for handoff-aware execution.
 * 
 * <p>This implements the Root Agent Orchestration Pattern as described in ADR-BFA-006.
 * When an agent executes a tool with handoff context, it returns this result
 * which wraps both the tool's output and metadata about how execution completed.</p>
 * 
 * <h3>Usage Pattern:</h3>
 * <pre>{@code
 * public AgentExecutionResult executeWithHandoff(
 *         String toolId,
 *         Map<String, Object> input,
 *         AgentHandoffContext handoff) {
 *     
 *     try {
 *         Map<String, Object> result = executeTool(toolId, input);
 *         return AgentExecutionResult.completed(result);
 *     } catch (OutOfScopeException e) {
 *         return AgentExecutionResult.outOfScope("banking-operations");
 *     } catch (Exception e) {
 *         return AgentExecutionResult.error(e.getMessage());
 *     }
 * }
 * }</pre>
 * 
 * @see AgentHandoffContext
 * @see AgentReturnContext
 * @see com.voicebanking.agent.Agent#executeWithHandoff
 * @author Augment Agent
 * @since 2026-02-03
 */
public record AgentExecutionResult(
    /** The result data from tool execution */
    Map<String, Object> resultData,
    
    /** The return context describing how execution completed */
    AgentReturnContext returnContext
) {
    
    /**
     * Create a successful completion result.
     * 
     * @param resultData the tool execution result
     * @return a new AgentExecutionResult
     */
    public static AgentExecutionResult completed(Map<String, Object> resultData) {
        return new AgentExecutionResult(
            resultData,
            AgentReturnContext.completed(resultData)
        );
    }
    
    /**
     * Create a completion result with updated conversation context.
     * 
     * @param resultData the tool execution result
     * @param conversationContext updated conversation context
     * @return a new AgentExecutionResult
     */
    public static AgentExecutionResult completed(
            Map<String, Object> resultData,
            Map<String, Object> conversationContext) {
        return new AgentExecutionResult(
            resultData,
            AgentReturnContext.completed(resultData, conversationContext)
        );
    }
    
    /**
     * Create a result indicating human handover is needed.
     * 
     * @param reason the reason for handover
     * @return a new AgentExecutionResult
     */
    public static AgentExecutionResult needsHandover(String reason) {
        return new AgentExecutionResult(
            Map.of("handoverRequired", true, "reason", reason),
            AgentReturnContext.needsHandover(reason)
        );
    }
    
    /**
     * Create a result indicating the request was out of scope.
     * 
     * @param targetIntent suggested intent to route to
     * @return a new AgentExecutionResult
     */
    public static AgentExecutionResult outOfScope(String targetIntent) {
        return new AgentExecutionResult(
            Map.of("outOfScope", true, "suggestedIntent", targetIntent),
            AgentReturnContext.outOfScope(targetIntent)
        );
    }
    
    /**
     * Create an error result.
     * 
     * @param errorMessage the error message
     * @return a new AgentExecutionResult
     */
    public static AgentExecutionResult error(String errorMessage) {
        return new AgentExecutionResult(
            Map.of("error", true, "message", errorMessage),
            AgentReturnContext.error(errorMessage)
        );
    }
    
    /**
     * Create a result indicating user cancellation.
     * 
     * @return a new AgentExecutionResult
     */
    public static AgentExecutionResult userCancelled() {
        return new AgentExecutionResult(
            Map.of("cancelled", true),
            AgentReturnContext.userCancelled()
        );
    }
    
    /**
     * Check if this result indicates success.
     * 
     * @return true if execution completed successfully
     */
    public boolean isSuccess() {
        return returnContext.isSuccess();
    }
    
    /**
     * Check if this result indicates an error.
     * 
     * @return true if execution failed with an error
     */
    public boolean isError() {
        return returnContext.isError();
    }
    
    /**
     * Check if this result requires human intervention.
     * 
     * @return true if human handover is needed
     */
    public boolean needsHumanIntervention() {
        return returnContext.needsHumanIntervention();
    }
    
    /**
     * Get the return reason.
     * 
     * @return the return reason from the return context
     */
    public AgentReturnContext.ReturnReason returnReason() {
        return returnContext.returnReason();
    }
}
