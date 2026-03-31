package com.voicebanking.agent.policy;

import com.voicebanking.agent.Agent;
import com.voicebanking.agent.policy.domain.*;
import com.voicebanking.agent.policy.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.*;
import java.util.UUID;

@Component
public class PolicyGuardrailsAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(PolicyGuardrailsAgent.class);
    private static final String AGENT_ID = "policy-guardrails";
    private static final List<String> TOOL_IDS = List.of(
        "classifyIntent", "checkPolicyViolation", "getRefusalMessage",
        "getAlternativeChannel", "logPolicyEvent", "blockProhibitedAction");

    private final IntentClassifierService classifierService;
    private final RefusalMessageService refusalMessageService;
    private final PolicyLoggingService loggingService;

    public PolicyGuardrailsAgent(IntentClassifierService classifierService,
                                  RefusalMessageService refusalMessageService,
                                  PolicyLoggingService loggingService) {
        this.classifierService = classifierService;
        this.refusalMessageService = refusalMessageService;
        this.loggingService = loggingService;
        log.info("PolicyGuardrailsAgent initialized with {} tools", TOOL_IDS.size());
    }

    @Override public String getAgentId() { return AGENT_ID; }
    @Override public String getDescription() { return "Policy guardrails agent for out-of-scope refusal and safety controls"; }
    @Override public List<String> getToolIds() { return TOOL_IDS; }

    @Override
    public Map<String, Object> executeTool(String toolId, Map<String, Object> input) {
        log.debug("PolicyGuardrailsAgent executing tool: {} with input: {}", toolId, input);
        return switch (toolId) {
            case "classifyIntent" -> executeClassifyIntent(input);
            case "checkPolicyViolation" -> executeCheckPolicyViolation(input);
            case "getRefusalMessage" -> executeGetRefusalMessage(input);
            case "getAlternativeChannel" -> executeGetAlternativeChannel(input);
            case "logPolicyEvent" -> executeLogPolicyEvent(input);
            case "blockProhibitedAction" -> executeBlockProhibitedAction(input);
            default -> throw new IllegalArgumentException("Unknown tool: " + toolId);
        };
    }

    private Map<String, Object> executeClassifyIntent(Map<String, Object> input) {
        String intent = (String) input.get("intent");
        String rawText = (String) input.get("rawText");
        IntentClassification classification = classifierService.classify(intent, rawText);
        Map<String, Object> result = new HashMap<>();
        result.put("category", classification.category().name());
        result.put("confidence", classification.confidence());
        result.put("matchedRuleId", classification.matchedRuleId());
        result.put("matchedBy", classification.matchedBy().name());
        return result;
    }

    private Map<String, Object> executeCheckPolicyViolation(Map<String, Object> input) {
        String intent = (String) input.get("intent");
        String rawText = (String) input.get("rawText");
        PolicyDecision decision = classifierService.evaluateIntent(intent, rawText);
        Map<String, Object> result = new HashMap<>();
        result.put("violation", !decision.isAllowed());
        result.put("category", decision.category().name());
        result.put("decision", decision.decision().name());
        result.put("message", decision.message());
        result.put("alternatives", decision.alternatives());
        result.put("correlationId", decision.correlationId());
        return result;
    }

    private Map<String, Object> executeGetRefusalMessage(Map<String, Object> input) {
        String categoryStr = (String) input.get("category");
        PolicyCategory category = PolicyCategory.valueOf(categoryStr);
        RefusalResult refusal = refusalMessageService.getRefusalMessage(category);
        Map<String, Object> result = new HashMap<>();
        result.put("message", refusal.message());
        result.put("alternatives", refusal.alternatives());
        result.put("handoverOffered", refusal.handoverOffered());
        result.put("templateId", refusal.templateId());
        return result;
    }

    private Map<String, Object> executeGetAlternativeChannel(Map<String, Object> input) {
        String categoryStr = (String) input.get("category");
        PolicyCategory category = PolicyCategory.valueOf(categoryStr);
        List<String> alternatives = refusalMessageService.getAlternativesForCategory(category);
        Map<String, Object> result = new HashMap<>();
        result.put("channels", alternatives);
        return result;
    }

    private Map<String, Object> executeLogPolicyEvent(Map<String, Object> input) {
        String intent = (String) input.get("intent");
        String categoryStr = (String) input.get("category");
        String decisionStr = (String) input.get("decision");
        String correlationId = (String) input.getOrDefault("correlationId", UUID.randomUUID().toString());
        PolicyCategory category = PolicyCategory.valueOf(categoryStr);
        PolicyDecision.Decision decision = PolicyDecision.Decision.valueOf(decisionStr);
        PolicyDecision policyDecision = new PolicyDecision(decision, category, "", List.of(), correlationId, null, intent, 1.0);
        String eventId = loggingService.logPolicyDecision(policyDecision, correlationId);
        Map<String, Object> result = new HashMap<>();
        result.put("logged", true);
        result.put("eventId", eventId);
        return result;
    }

    private Map<String, Object> executeBlockProhibitedAction(Map<String, Object> input) {
        String reason = (String) input.getOrDefault("reason", "SECURITY");
        String intent = (String) input.get("intent");
        String correlationId = (String) input.getOrDefault("correlationId", UUID.randomUUID().toString());
        PolicyCategory category = reason.equals("SECURITY") ? PolicyCategory.SECURITY_VIOLATION : PolicyCategory.HARMFUL;
        PolicyDecision decision = PolicyDecision.block(category, "This action is prohibited for security reasons.", null);
        loggingService.logPolicyDecision(decision, correlationId);
        Map<String, Object> result = new HashMap<>();
        result.put("blocked", true);
        result.put("message", decision.message());
        result.put("correlationId", decision.correlationId());
        return result;
    }
}
