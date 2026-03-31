package com.voicebanking.agent.handover.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HandoverContext {
    private String sessionId;
    private String customerId;
    private String conversationSummary;
    private String detectedIntent;
    private Map<String, Object> entities = new HashMap<>();
    private List<String> toolsCalled = new ArrayList<>();
    private HandoverReason handoverReason;
    private String policyCategory;
    private List<ConversationTurnContext> conversationHistory = new ArrayList<>();
    private String customerSentiment;
    private Instant startedAt;
    private Instant handoverAt;
    private Map<String, Object> additionalMetadata = new HashMap<>();

    private HandoverContext() {}

    public String getSessionId() { return sessionId; }
    public String getCustomerId() { return customerId; }
    public String getConversationSummary() { return conversationSummary; }
    public String getDetectedIntent() { return detectedIntent; }
    public Map<String, Object> getEntities() { return Map.copyOf(entities); }
    public List<String> getToolsCalled() { return List.copyOf(toolsCalled); }
    public HandoverReason getHandoverReason() { return handoverReason; }
    public String getPolicyCategory() { return policyCategory; }
    public List<ConversationTurnContext> getConversationHistory() { return List.copyOf(conversationHistory); }
    public String getCustomerSentiment() { return customerSentiment; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getHandoverAt() { return handoverAt; }
    public Map<String, Object> getAdditionalMetadata() { return Map.copyOf(additionalMetadata); }

    public long getConversationDurationSeconds() {
        if (startedAt == null || handoverAt == null) return 0;
        return Duration.between(startedAt, handoverAt).getSeconds();
    }

    public int getTurnCount() { return conversationHistory.size(); }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("sessionId", sessionId);
        map.put("customerId", customerId);
        map.put("conversationSummary", conversationSummary);
        map.put("detectedIntent", detectedIntent);
        map.put("entities", entities);
        map.put("toolsCalled", toolsCalled);
        map.put("handoverReason", handoverReason != null ? handoverReason.name() : null);
        map.put("policyCategory", policyCategory);
        map.put("customerSentiment", customerSentiment);
        map.put("startedAt", startedAt != null ? startedAt.toString() : null);
        map.put("handoverAt", handoverAt != null ? handoverAt.toString() : null);
        map.put("conversationDurationSeconds", getConversationDurationSeconds());
        map.put("turnCount", getTurnCount());
        return map;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final HandoverContext ctx = new HandoverContext();

        public Builder sessionId(String s) { ctx.sessionId = s; return this; }
        public Builder customerId(String s) { ctx.customerId = s; return this; }
        public Builder conversationSummary(String s) { ctx.conversationSummary = s; return this; }
        public Builder detectedIntent(String s) { ctx.detectedIntent = s; return this; }
        public Builder entities(Map<String, Object> m) { ctx.entities = new HashMap<>(m); return this; }
        public Builder addEntity(String k, Object v) { ctx.entities.put(k, v); return this; }
        public Builder toolsCalled(List<String> l) { ctx.toolsCalled = new ArrayList<>(l); return this; }
        public Builder addToolCalled(String t) { ctx.toolsCalled.add(t); return this; }
        public Builder handoverReason(HandoverReason r) { ctx.handoverReason = r; return this; }
        public Builder policyCategory(String s) { ctx.policyCategory = s; return this; }
        public Builder conversationHistory(List<ConversationTurnContext> l) { ctx.conversationHistory = new ArrayList<>(l); return this; }
        public Builder addConversationTurn(ConversationTurnContext t) { ctx.conversationHistory.add(t); return this; }
        public Builder customerSentiment(String s) { ctx.customerSentiment = s; return this; }
        public Builder startedAt(Instant i) { ctx.startedAt = i; return this; }
        public Builder handoverAt(Instant i) { ctx.handoverAt = i; return this; }
        public Builder additionalMetadata(Map<String, Object> m) { ctx.additionalMetadata = new HashMap<>(m); return this; }

        public HandoverContext build() {
            if (ctx.sessionId == null || ctx.sessionId.isBlank()) throw new IllegalStateException("sessionId is required");
            if (ctx.handoverReason == null) throw new IllegalStateException("handoverReason is required");
            if (ctx.handoverAt == null) ctx.handoverAt = Instant.now();
            return ctx;
        }
    }
}
