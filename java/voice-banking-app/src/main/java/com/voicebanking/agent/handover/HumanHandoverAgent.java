package com.voicebanking.agent.handover;

import com.voicebanking.agent.Agent;
import com.voicebanking.agent.handover.domain.*;
import com.voicebanking.agent.handover.integration.CallCenterClient;
import com.voicebanking.agent.handover.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class HumanHandoverAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(HumanHandoverAgent.class);

    private static final String AGENT_ID = "human-handover";
    private static final List<String> TOOL_IDS = List.of(
            "initiateHandover",
            "buildContextPayload",
            "checkAgentAvailability",
            "getQueueWaitTime",
            "routeToQueue",
            "sendHandoverNotification"
    );

    private final ContextBuilderService contextBuilderService;
    private final QueueStatusService queueStatusService;
    private final HandoverLogService handoverLogService;
    private final CallCenterClient callCenterClient;
    private final String defaultQueueId;

    public HumanHandoverAgent(
            ContextBuilderService contextBuilderService,
            QueueStatusService queueStatusService,
            HandoverLogService handoverLogService,
            CallCenterClient callCenterClient,
            @Value("${handover.default-queue:general}") String defaultQueueId) {
        this.contextBuilderService = contextBuilderService;
        this.queueStatusService = queueStatusService;
        this.handoverLogService = handoverLogService;
        this.callCenterClient = callCenterClient;
        this.defaultQueueId = defaultQueueId;
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    @Override
    public String getDescription() {
        return "Handles escalation to human agents when AI cannot resolve customer requests";
    }

    @Override
    public List<String> getToolIds() {
        return TOOL_IDS;
    }

    @Override
    public Map<String, Object> executeTool(String toolId, Map<String, Object> input) {
        log.debug("Executing tool: {} with input: {}", toolId, input);

        try {
            return switch (toolId) {
                case "initiateHandover" -> initiateHandover(input);
                case "buildContextPayload" -> buildContextPayload(input);
                case "checkAgentAvailability" -> checkAgentAvailability(input);
                case "getQueueWaitTime" -> getQueueWaitTime(input);
                case "routeToQueue" -> routeToQueue(input);
                case "sendHandoverNotification" -> sendHandoverNotification(input);
                default -> Map.of("error", "Unknown tool: " + toolId, "success", false);
            };
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolId, e.getMessage(), e);
            return Map.of("error", e.getMessage(), "success", false);
        }
    }

    private Map<String, Object> initiateHandover(Map<String, Object> input) {
        String sessionId = (String) input.get("sessionId");
        String reasonStr = (String) input.getOrDefault("reason", "USER_REQUEST");
        String queueId = (String) input.getOrDefault("queueId", defaultQueueId);

        HandoverReason reason = HandoverReason.valueOf(reasonStr);
        HandoverContext context = contextBuilderService.buildContext(sessionId, reason);
        
        handoverLogService.logHandoverInitiated(sessionId, context);

        boolean available = queueStatusService.checkAgentAvailability(queueId);
        if (!available) {
            QueueStatus status = queueStatusService.getQueueStatus(queueId);
            if (!status.withinBusinessHours()) {
                return Map.of(
                        "success", false,
                        "status", "AFTER_HOURS",
                        "message", "Our call center is currently closed. Please try again during business hours (09:00-18:00 CET, Monday-Friday).",
                        "context", context.toMap()
                );
            }
        }

        HandoverResult result = callCenterClient.routeHandover(queueId, context);
        handoverLogService.logHandoverCompleted(sessionId, result);

        Map<String, Object> response = new HashMap<>(result.toMap());
        response.put("context", context.toMap());
        return response;
    }

    private Map<String, Object> buildContextPayload(Map<String, Object> input) {
        String sessionId = (String) input.get("sessionId");
        String reasonStr = (String) input.getOrDefault("reason", "USER_REQUEST");
        
        HandoverReason reason = HandoverReason.valueOf(reasonStr);
        HandoverContext context = contextBuilderService.buildContext(sessionId, reason);
        
        return Map.of(
                "success", true,
                "context", context.toMap()
        );
    }

    private Map<String, Object> checkAgentAvailability(Map<String, Object> input) {
        String queueId = (String) input.getOrDefault("queueId", defaultQueueId);
        
        boolean available = queueStatusService.checkAgentAvailability(queueId);
        QueueStatus status = queueStatusService.getQueueStatus(queueId);
        
        handoverLogService.logQueueStatusCheck(queueId, available, status.estimatedWaitMinutes());

        return Map.of(
                "success", true,
                "available", available,
                "queueId", queueId,
                "withinBusinessHours", status.withinBusinessHours(),
                "agentCount", status.agentCount(),
                "queueDepth", status.queueDepth()
        );
    }

    private Map<String, Object> getQueueWaitTime(Map<String, Object> input) {
        String queueId = (String) input.getOrDefault("queueId", defaultQueueId);
        
        QueueStatus status = queueStatusService.getQueueStatus(queueId);
        
        return Map.of(
                "success", true,
                "queueId", queueId,
                "estimatedWaitMinutes", status.estimatedWaitMinutes(),
                "message", status.getWaitTimeMessage(),
                "available", status.canAcceptHandover()
        );
    }

    private Map<String, Object> routeToQueue(Map<String, Object> input) {
        String sessionId = (String) input.get("sessionId");
        String queueId = (String) input.getOrDefault("queueId", defaultQueueId);
        String reasonStr = (String) input.getOrDefault("reason", "USER_REQUEST");
        
        HandoverReason reason = HandoverReason.valueOf(reasonStr);
        HandoverContext context = contextBuilderService.buildContext(sessionId, reason);
        
        HandoverResult result = callCenterClient.routeHandover(queueId, context);
        
        if (result.isSuccess()) {
            handoverLogService.logHandoverRouted(sessionId, queueId, 
                    queueStatusService.getEstimatedWaitTime(queueId));
        }
        
        return result.toMap();
    }

    private Map<String, Object> sendHandoverNotification(Map<String, Object> input) {
        String ticketId = (String) input.get("ticketId");
        String sessionId = (String) input.get("sessionId");
        String reasonStr = (String) input.getOrDefault("reason", "USER_REQUEST");
        
        HandoverReason reason = HandoverReason.valueOf(reasonStr);
        HandoverContext context = contextBuilderService.buildContext(sessionId, reason);
        
        boolean sent = callCenterClient.sendContext(ticketId, context);
        
        if (sent) {
            String json = context.toMap().toString();
            handoverLogService.logContextSent(sessionId, json.length());
        }
        
        return Map.of(
                "success", sent,
                "ticketId", ticketId,
                "message", sent ? "Context sent to agent desktop" : "Failed to send context"
        );
    }
}
