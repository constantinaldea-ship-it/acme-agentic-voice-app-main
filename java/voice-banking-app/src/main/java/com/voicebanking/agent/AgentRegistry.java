package com.voicebanking.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent Registry
 * 
 * Discovers and registers all functional agents in the system.
 * Provides routing capabilities to find the appropriate agent for a given tool.
 * 
 * Architecture Reference: Component D (AI Agent Orchestrator) from Acme Bank Architecture
 * 
 * @author Augment Agent
 * @since 2026-01-22
 */
@Service
public class AgentRegistry {
    
    private static final Logger log = LoggerFactory.getLogger(AgentRegistry.class);
    
    private final Map<String, Agent> agentById = new ConcurrentHashMap<>();
    private final Map<String, Agent> toolToAgent = new ConcurrentHashMap<>();
    
    /**
     * Constructor - automatically discovers and registers all agents in the application context.
     * 
     * @param agents all Agent beans discovered by Spring
     */
    public AgentRegistry(List<Agent> agents) {
        registerAgents(agents);
    }
    
    /**
     * Register all discovered agents and build tool-to-agent mapping.
     */
    private void registerAgents(List<Agent> agents) {
        for (Agent agent : agents) {
            agentById.put(agent.getAgentId(), agent);
            
            // Map each tool to its agent
            for (String toolId : agent.getToolIds()) {
                if (toolToAgent.containsKey(toolId)) {
                    log.warn("Tool '{}' is provided by multiple agents: {} and {}",
                            toolId, toolToAgent.get(toolId).getAgentId(), agent.getAgentId());
                }
                toolToAgent.put(toolId, agent);
            }
            
            log.info("Registered agent '{}' with {} tools: {}",
                    agent.getAgentId(), agent.getToolIds().size(), agent.getToolIds());
        }
        
        log.info("AgentRegistry initialized with {} agents and {} tools",
                agentById.size(), toolToAgent.size());
    }
    
    /**
     * Get an agent by its unique identifier.
     * 
     * @param agentId the agent ID
     * @return the agent, or empty if not found
     */
    public Optional<Agent> getAgent(String agentId) {
        return Optional.ofNullable(agentById.get(agentId));
    }
    
    /**
     * Find the agent that provides a specific tool.
     * 
     * @param toolId the tool identifier
     * @return the agent that provides the tool, or empty if not found
     */
    public Optional<Agent> findAgentForTool(String toolId) {
        return Optional.ofNullable(toolToAgent.get(toolId));
    }
    
    /**
     * Execute a tool by automatically routing to the appropriate agent.
     * 
     * @param toolId the tool to execute
     * @param input the input parameters
     * @return the tool execution result
     * @throws IllegalArgumentException if tool is not found or execution fails
     */
    public Map<String, Object> executeTool(String toolId, Map<String, Object> input) {
        Agent agent = toolToAgent.get(toolId);
        if (agent == null) {
            throw new IllegalArgumentException("No agent found for tool: " + toolId);
        }
        
        log.debug("Routing tool '{}' to agent '{}'", toolId, agent.getAgentId());
        return agent.executeTool(toolId, input);
    }
    
    /**
     * Get all registered agents.
     * 
     * @return list of all agents
     */
    public List<Agent> getAllAgents() {
        return new ArrayList<>(agentById.values());
    }
    
    /**
     * Get all available tool IDs across all agents.
     * 
     * @return set of all tool identifiers
     */
    public Set<String> getAllToolIds() {
        return new HashSet<>(toolToAgent.keySet());
    }
}
