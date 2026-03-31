package com.voicebanking.agent.context;

import java.time.Instant;
import java.util.Optional;

/**
 * Agent Context
 * 
 * Thread-local context for the currently executing agent.
 * Used to propagate agent identity to downstream services (BFA).
 * 
 * <p>This implements the Root Agent Orchestration Pattern as described in ADR-BFA-006.
 * The context is set when a tool is dispatched to an agent and cleared after execution.</p>
 * 
 * <h3>Usage Pattern:</h3>
 * <pre>{@code
 * AgentContext.setCurrent(new AgentContext(agentId, toolId, sessionId));
 * try {
 *     agent.executeTool(toolId, input);
 * } finally {
 *     AgentContext.clear();
 * }
 * }</pre>
 * 
 * <h3>Downstream Propagation:</h3>
 * <pre>{@code
 * AgentContext.getCurrent().ifPresent(ctx -> {
 *     headers.set("X-Agent-Id", ctx.agentId());
 *     headers.set("X-Tool-Id", ctx.toolId());
 * });
 * }</pre>
 * 
 * @see com.voicebanking.agent.AgentRegistry
 * @author Augment Agent
 * @since 2026-02-03
 */
public record AgentContext(
    /** The agent executing the current request */
    String agentId,
    
    /** The tool being executed */
    String toolId,
    
    /** The session ID for the current conversation */
    String sessionId,
    
    /** Timestamp when this context was created */
    Instant startTime
) {
    
    private static final ThreadLocal<AgentContext> CURRENT = new ThreadLocal<>();
    
    /**
     * Create a new AgentContext with current timestamp.
     * 
     * @param agentId the agent identifier
     * @param toolId the tool identifier
     * @param sessionId the session identifier
     */
    public AgentContext(String agentId, String toolId, String sessionId) {
        this(agentId, toolId, sessionId, Instant.now());
    }
    
    /**
     * Set the current context for this thread.
     * 
     * @param context the context to set
     */
    public static void setCurrent(AgentContext context) {
        CURRENT.set(context);
    }
    
    /**
     * Get the current context for this thread.
     * 
     * @return the current context, or empty if not set
     */
    public static Optional<AgentContext> getCurrent() {
        return Optional.ofNullable(CURRENT.get());
    }
    
    /**
     * Clear the current context for this thread.
     * Should always be called in a finally block.
     */
    public static void clear() {
        CURRENT.remove();
    }
    
    /**
     * Execute a block with this context set.
     * Automatically clears the context after execution.
     * 
     * @param runnable the block to execute
     */
    public void executeWith(Runnable runnable) {
        setCurrent(this);
        try {
            runnable.run();
        } finally {
            clear();
        }
    }
    
    /**
     * Execute a supplier with this context set.
     * Automatically clears the context after execution.
     * 
     * @param <T> the return type
     * @param supplier the supplier to execute
     * @return the result of the supplier
     */
    public <T> T executeWith(java.util.function.Supplier<T> supplier) {
        setCurrent(this);
        try {
            return supplier.get();
        } finally {
            clear();
        }
    }
    
    /**
     * Get the duration since this context was created.
     * 
     * @return duration in milliseconds
     */
    public long durationMs() {
        return java.time.Duration.between(startTime, Instant.now()).toMillis();
    }
}
