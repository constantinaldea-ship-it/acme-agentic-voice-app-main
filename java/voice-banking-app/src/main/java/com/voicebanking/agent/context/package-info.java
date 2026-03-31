/**
 * Agent Context Package
 * 
 * <p>Contains classes for managing agent execution context in the
 * Root Agent Orchestration Pattern (ADR-BFA-006).</p>
 * 
 * <h2>Key Classes</h2>
 * <ul>
 *   <li>{@link com.voicebanking.agent.context.AgentContext} - ThreadLocal for current agent identity</li>
 *   <li>{@link com.voicebanking.agent.context.AgentHandoffContext} - Context passed during agent handoff</li>
 *   <li>{@link com.voicebanking.agent.context.AgentReturnContext} - Context returned after agent execution</li>
 *   <li>{@link com.voicebanking.agent.context.AgentExecutionResult} - Wrapper for tool execution result</li>
 * </ul>
 * 
 * <h2>Architecture Reference</h2>
 * <p>See ADR-BFA-006: Root Agent Orchestration Pattern for design details.</p>
 * 
 * @author Augment Agent
 * @since 2026-02-03
 */
package com.voicebanking.agent.context;
