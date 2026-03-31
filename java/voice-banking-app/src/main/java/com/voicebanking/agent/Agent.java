package com.voicebanking.agent;

import com.voicebanking.agent.context.AgentExecutionResult;
import com.voicebanking.agent.context.AgentHandoffContext;
import com.voicebanking.agent.context.AgentReturnContext;

import java.util.List;
import java.util.Map;

/**
 * Agent Interface
 * 
 * Base abstraction for all functional agents in the AI Banking Voice system.
 * Each agent is a specialized module that handles a specific domain (e.g., banking operations,
 * location services, knowledge compilation).
 * 
 * <p>This interface supports the Root Agent Orchestration Pattern (ADR-BFA-006) with
 * handoff-aware execution methods for multi-agent orchestration.</p>
 * 
 * <h3>Basic Usage:</h3>
 * <pre>{@code
 * Map<String, Object> result = agent.executeTool("getBalance", input);
 * }</pre>
 * 
 * <h3>Handoff-Aware Usage:</h3>
 * <pre>{@code
 * AgentHandoffContext handoff = AgentHandoffContext.from(request, toolName);
 * AgentExecutionResult result = agent.executeWithHandoff(toolName, input, handoff);
 * if (!result.isSuccess()) {
 *     // Handle error, handover, or out-of-scope
 * }
 * }</pre>
 * 
 * Architecture Reference: Component E (AI Functional Agents) from Acme Bank Architecture
 * 
 * @see AgentExecutionResult
 * @see AgentHandoffContext
 * @see AgentReturnContext
 * @author Augment Agent
 * @since 2026-01-22
 */
public interface Agent {
    
    // ============================================================
    // Core Agent Identity Methods
    // ============================================================
    
    /**
     * Get the unique identifier for this agent.
     * Used by the orchestrator for routing and discovery.
     * 
     * @return agent ID (e.g., "banking-operations", "location-services")
     */
    String getAgentId();
    
    /**
     * Get a human-readable description of this agent's capabilities.
     * 
     * @return agent description
     */
    String getDescription();
    
    /**
     * Get the list of tool IDs that this agent provides.
     * Tools are atomic capabilities (e.g., "getBalance", "findNearbyBranches").
     * 
     * @return list of tool identifiers
     */
    List<String> getToolIds();
    
    // ============================================================
    // Tool Execution Methods
    // ============================================================
    
    /**
     * Execute a tool provided by this agent.
     * 
     * @param toolId the tool to execute
     * @param input the input parameters (key-value pairs)
     * @return the tool execution result
     * @throws IllegalArgumentException if tool is not supported or input is invalid
     */
    Map<String, Object> executeTool(String toolId, Map<String, Object> input);
    
    /**
     * Execute a tool with handoff context (Root Agent Pattern).
     * 
     * <p>This method is used by the root orchestrator when dispatching to
     * specialized agents. It provides full handoff context and returns
     * a structured result that includes return semantics.</p>
     * 
     * <p>The default implementation wraps the basic {@link #executeTool} method.
     * Override this method to implement custom handoff handling, such as
     * validation of handoff context or specialized error handling.</p>
     * 
     * @param toolId the tool to execute
     * @param input the input parameters
     * @param handoff the handoff context from the root agent
     * @return the execution result with return context
     */
    default AgentExecutionResult executeWithHandoff(
            String toolId,
            Map<String, Object> input,
            AgentHandoffContext handoff) {
        try {
            // Notify agent of incoming handoff
            onHandoffReceived(handoff);
            
            // Execute the tool
            Map<String, Object> result = executeTool(toolId, input);
            AgentExecutionResult executionResult = AgentExecutionResult.completed(result);
            
            // Notify agent of return
            onReturnToRoot(executionResult.returnContext());
            
            return executionResult;
        } catch (IllegalArgumentException e) {
            // Tool not supported or invalid input - might be out of scope
            return AgentExecutionResult.outOfScope(null);
        } catch (Exception e) {
            return AgentExecutionResult.error(e.getMessage());
        }
    }
    
    // ============================================================
    // Tool Capability Checks
    // ============================================================
    
    /**
     * Check if this agent can handle the given tool.
     * 
     * @param toolId the tool identifier
     * @return true if this agent supports the tool
     */
    default boolean supportsTool(String toolId) {
        return getToolIds().contains(toolId);
    }
    
    /**
     * Check if this agent can handle the given tool.
     * Alias for {@link #supportsTool(String)}.
     * 
     * @param toolId the tool identifier
     * @return true if this agent can handle the tool
     */
    default boolean canHandle(String toolId) {
        return supportsTool(toolId);
    }
    
    // ============================================================
    // Lifecycle Hooks (Root Agent Pattern)
    // ============================================================
    
    /**
     * Called when this agent receives a handoff from the root orchestrator.
     * 
     * <p>Override to perform initialization based on the handoff context,
     * such as validating consent or setting up agent-specific state.</p>
     * 
     * @param handoff the handoff context
     */
    default void onHandoffReceived(AgentHandoffContext handoff) {
        // Default: no-op, agents can override for custom initialization
    }
    
    /**
     * Called when this agent returns control to the root orchestrator.
     * 
     * <p>Override to perform cleanup or logging based on the return context.</p>
     * 
     * @param returnContext the return context describing how execution completed
     */
    default void onReturnToRoot(AgentReturnContext returnContext) {
        // Default: no-op, agents can override for custom cleanup
    }
}
