package com.voicebanking.agent.handover.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record HandoverResult(
    String handoverId,
    HandoverStatus status,
    int queuePosition,
    int estimatedWaitMinutes,
    String queueId,
    String customerMessage,
    String errorMessage,
    Instant timestamp
) {
    public enum HandoverStatus {
        INITIATED, QUEUED, CONNECTED, COMPLETED, FAILED, CANCELLED, FALLBACK
    }
    
    public static HandoverResult initiated(String handoverId) {
        return new HandoverResult(handoverId, HandoverStatus.INITIATED, 0, 0, null,
            "Your request to speak with an agent has been received.", null, Instant.now());
    }
    
    public static HandoverResult queued(String handoverId, String queueId, int position, int waitMinutes) {
        String message = position == 1 
            ? "You are next in line. An agent will be with you shortly."
            : String.format("You are number %d in the queue. Estimated wait: %d minutes.", position, waitMinutes);
        return new HandoverResult(handoverId, HandoverStatus.QUEUED, position, waitMinutes, queueId, message, null, Instant.now());
    }
    
    public static HandoverResult failed(String handoverId, String errorMessage) {
        return new HandoverResult(handoverId, HandoverStatus.FAILED, 0, 0, null,
            "We're unable to connect you with an agent at this time. Please try again or contact us at our support line.",
            errorMessage, Instant.now());
    }
    
    public static HandoverResult fallback(String handoverId, String contactInfo) {
        return new HandoverResult(handoverId, HandoverStatus.FALLBACK, 0, 0, null, contactInfo, null, Instant.now());
    }
    
    public boolean isSuccess() {
        return status == HandoverStatus.INITIATED || status == HandoverStatus.QUEUED 
            || status == HandoverStatus.CONNECTED || status == HandoverStatus.COMPLETED;
    }
    
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("handoverId", handoverId);
        map.put("status", status.name());
        map.put("queuePosition", queuePosition);
        map.put("estimatedWaitMinutes", estimatedWaitMinutes);
        map.put("queueId", queueId);
        map.put("customerMessage", customerMessage);
        map.put("errorMessage", errorMessage);
        map.put("timestamp", timestamp.toString());
        map.put("success", isSuccess());
        return map;
    }
}
