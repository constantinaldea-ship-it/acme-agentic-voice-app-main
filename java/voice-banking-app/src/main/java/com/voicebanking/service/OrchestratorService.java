package com.voicebanking.service;

import com.voicebanking.adapter.llm.LlmProvider;
import com.voicebanking.adapter.llm.LlmResponse;
import com.voicebanking.adapter.stt.SttProvider;
import com.voicebanking.adapter.stt.TranscriptResponse;
import com.voicebanking.agent.Agent;
import com.voicebanking.agent.AgentRegistry;
import com.voicebanking.agent.context.AgentContext;
import com.voicebanking.agent.context.AgentExecutionResult;
import com.voicebanking.agent.context.AgentHandoffContext;
import com.voicebanking.domain.dto.OrchestratorRequest;
import com.voicebanking.domain.dto.OrchestratorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Orchestrator Service
 * 
 * <p>Main orchestration service that coordinates the voice banking flow:</p>
 * <ol>
 *   <li>Validate request and consent</li>
 *   <li>Speech-to-Text (if audio provided)</li>
 *   <li>LLM intent detection</li>
 *   <li>Policy gate evaluation</li>
 *   <li>Tool execution via AgentRegistry (Root Agent Pattern)</li>
 *   <li>Response generation</li>
 * </ol>
 * 
 * <p>This service implements the Root Agent Orchestration Pattern (ADR-BFA-006)
 * by routing tool calls through the {@link AgentRegistry} rather than a flat
 * tool registry. This preserves agent context for downstream services.</p>
 * 
 * @see AgentRegistry
 * @see AgentContext
 */
@Service
public class OrchestratorService {
    
    private static final Logger log = LoggerFactory.getLogger(OrchestratorService.class);
    
    private final SttProvider sttProvider;
    private final LlmProvider llmProvider;
    private final PolicyGateService policyGate;
    private final AgentRegistry agentRegistry;
    
    public OrchestratorService(
            SttProvider sttProvider,
            LlmProvider llmProvider,
            PolicyGateService policyGate,
            AgentRegistry agentRegistry) {
        this.sttProvider = sttProvider;
        this.llmProvider = llmProvider;
        this.policyGate = policyGate;
        this.agentRegistry = agentRegistry;
        log.info("OrchestratorService initialized with AgentRegistry (Root Agent Pattern)");
    }
    
    /**
     * Process a voice/text request
     * 
     * @param request Orchestrator request
     * @return Orchestrator response
     */
    public OrchestratorResponse process(OrchestratorRequest request) {
        log.info("Processing request for session: {}", request.sessionId());
        
        // Step 1: Validate request
        var violations = request.validate();
        if (!violations.isValid()) {
            String errors = violations.stream()
                .map(v -> v.name() + ": " + v.message())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Invalid request");
            log.error("Request validation failed: {}", errors);
            return OrchestratorResponse.refusal("", "validation_error", errors);
        }
        
        // Step 2: Speech-to-Text (if audio provided) or use text
        String transcript;
        if (request.audio() != null) {
            log.debug("Processing audio input");
            TranscriptResponse transcriptResp = sttProvider.transcribe(request.audio());
            transcript = transcriptResp.text();
            log.info("Transcribed: '{}' (confidence: {})", transcript, transcriptResp.confidence());
        } else {
            transcript = request.text();
            log.info("Using text input: '{}'", transcript);
        }
        
        // Step 3: LLM intent detection
        Map<String, Object> context = buildContext(request);
        LlmResponse llmResponse = llmProvider.process(transcript, context);
        log.info("LLM response type: {}", llmResponse.type());
        
        // Step 4: Handle response based on type
        return switch (llmResponse.type()) {
            case TOOL_CALL -> handleToolCall(transcript, llmResponse, request);
            case REFUSAL -> OrchestratorResponse.refusal(
                transcript,
                "llm_refusal",
                llmResponse.message()
            );
            case CLARIFICATION -> OrchestratorResponse.success(
                transcript,
                "clarification",
                null,
                null,
                llmResponse.message()
            );
            case DIRECT_RESPONSE -> OrchestratorResponse.success(
                transcript,
                "direct_response",
                null,
                null,
                llmResponse.message()
            );
        };
    }
    
    /**
     * Handle tool call response using Root Agent Pattern (ADR-BFA-006)
     * 
     * <p>Routes tool calls through {@link AgentRegistry} to preserve agent context.
     * Sets up {@link AgentContext} ThreadLocal for downstream propagation.</p>
     */
    private OrchestratorResponse handleToolCall(
            String transcript,
            LlmResponse llmResponse,
            OrchestratorRequest request) {
        
        var toolCall = llmResponse.toolCall();
        String toolName = toolCall.toolName();
        
        log.info("Handling tool call: {}", toolName);
        
        // Step 4: Policy gate evaluation
        var policyResult = policyGate.evaluateToolCall(toolName, request.consentAccepted());
        if (!policyResult.allowed()) {
            log.warn("Policy gate blocked tool: {}", toolName);
            return OrchestratorResponse.refusal(
                transcript,
                "policy_violation",
                policyResult.reason()
            );
        }
        
        // Step 5: Find agent for tool (Root Agent Pattern)
        Agent agent = agentRegistry.findAgentForTool(toolName)
            .orElseThrow(() -> new IllegalArgumentException("No agent found for tool: " + toolName));
        
        log.info("Routing tool '{}' to agent '{}'", toolName, agent.getAgentId());
        
        // Step 6: Set agent context for downstream propagation
        AgentContext agentContext = new AgentContext(
            agent.getAgentId(),
            toolName,
            request.sessionId()
        );
        
        AgentContext.setCurrent(agentContext);
        try {
            // Step 7: Create handoff context and execute via agent
            AgentHandoffContext handoff = AgentHandoffContext.from(request, toolName);
            AgentExecutionResult executionResult = agent.executeWithHandoff(
                toolName, 
                toolCall.input(), 
                handoff
            );
            
            // Step 8: Handle execution result based on return reason
            return handleExecutionResult(transcript, llmResponse, toolName, executionResult);
            
        } catch (Exception e) {
            log.error("Tool execution failed: {} (agent: {})", toolName, agent.getAgentId(), e);
            return OrchestratorResponse.refusal(
                transcript,
                "tool_execution_error",
                "I encountered an error while processing your request: " + e.getMessage()
            );
        } finally {
            // Always clear agent context
            AgentContext.clear();
            log.debug("Agent context cleared. Execution time: {}ms", agentContext.durationMs());
        }
    }
    
    /**
     * Handle the execution result from an agent.
     * Maps AgentExecutionResult to OrchestratorResponse.
     */
    private OrchestratorResponse handleExecutionResult(
            String transcript,
            LlmResponse llmResponse,
            String toolName,
            AgentExecutionResult result) {
        
        return switch (result.returnReason()) {
            case COMPLETED -> {
                String responseText = generateResponseText(toolName, result.resultData());
                yield OrchestratorResponse.success(
                    transcript,
                    llmResponse.message(),
                    toolName,
                    result.resultData(),
                    responseText
                );
            }
            case NEEDS_HANDOVER -> {
                log.info("Agent requested human handover");
                String reason = (String) result.resultData().getOrDefault("reason", "Agent requested assistance");
                yield OrchestratorResponse.refusal(
                    transcript,
                    "handover_required",
                    "I need to connect you with a human agent. " + reason
                );
            }
            case OUT_OF_SCOPE -> {
                String targetIntent = result.returnContext().targetIntent();
                log.info("Request out of agent scope, suggested intent: {}", targetIntent);
                yield OrchestratorResponse.refusal(
                    transcript,
                    "out_of_scope",
                    "I'm not able to help with that request. Please try rephrasing."
                );
            }
            case ERROR -> {
                String errorMsg = (String) result.resultData().getOrDefault("message", "Unknown error");
                yield OrchestratorResponse.refusal(
                    transcript,
                    "agent_error",
                    "I encountered an error: " + errorMsg
                );
            }
            case USER_CANCELLED -> OrchestratorResponse.success(
                transcript,
                "cancelled",
                null,
                null,
                "Okay, I've cancelled that request."
            );
        };
    }
    
    /**
     * Build context for LLM processing
     */
    private Map<String, Object> buildContext(OrchestratorRequest request) {
        var context = new HashMap<String, Object>();
        context.put("sessionId", request.sessionId());
        context.put("consentAccepted", request.consentAccepted());
        // Future: Add conversation history, resolved entities, etc.
        return context;
    }
    
    /**
     * Generate human-readable response text based on tool result
     */
    private String generateResponseText(String toolName, Object toolResult) {
        return switch (toolName) {
            case "getBalance" -> "Here is your balance information.";
            case "listAccounts" -> "Here are your accounts.";
            case "queryTransactions" -> "Here are your recent transactions.";
            default -> "Here is the requested information.";
        };
    }
}
