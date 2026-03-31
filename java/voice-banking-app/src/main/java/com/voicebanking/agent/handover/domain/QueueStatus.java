package com.voicebanking.agent.handover.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record QueueStatus(
    String queueId,
    String queueName,
    boolean available,
    int agentCount,
    int queueDepth,
    int estimatedWaitMinutes,
    boolean withinBusinessHours,
    Instant asOf
) {
    public static QueueStatus closed(String queueId, String reason) {
        return new QueueStatus(queueId, reason, false, 0, 0, 0, false, Instant.now());
    }
    
    public static QueueStatus afterHours(String queueId) {
        return new QueueStatus(queueId, "After Hours", false, 0, 0, 0, false, Instant.now());
    }
    
    public boolean canAcceptHandover() {
        return available && withinBusinessHours && agentCount > 0;
    }
    
    public String getWaitTimeMessage() {
        if (!withinBusinessHours) {
            return "Our agents are currently unavailable. Please try again during business hours.";
        }
        if (!available || agentCount == 0) {
            return "All agents are currently busy. Please hold for the next available agent.";
        }
        if (estimatedWaitMinutes <= 1) {
            return "An agent will be with you shortly.";
        }
        if (estimatedWaitMinutes <= 5) {
            return String.format("Estimated wait time is approximately %d minutes.", estimatedWaitMinutes);
        }
        return String.format("Due to high call volume, the estimated wait time is %d minutes.", estimatedWaitMinutes);
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("queueId", queueId);
        map.put("queueName", queueName);
        map.put("available", available);
        map.put("agentCount", agentCount);
        map.put("queueDepth", queueDepth);
        map.put("estimatedWaitMinutes", estimatedWaitMinutes);
        map.put("withinBusinessHours", withinBusinessHours);
        map.put("canAcceptHandover", canAcceptHandover());
        map.put("waitTimeMessage", getWaitTimeMessage());
        map.put("asOf", asOf.toString());
        return map;
    }
}
