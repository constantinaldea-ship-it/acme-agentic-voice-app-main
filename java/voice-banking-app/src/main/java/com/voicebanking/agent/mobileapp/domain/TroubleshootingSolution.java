package com.voicebanking.agent.mobileapp.domain;

import java.util.*;
import java.util.stream.Collectors;

/**
 * A troubleshooting solution for a mobile app issue.
 * 
 * Contains diagnostic steps, solutions, and escalation path
 * if the issue cannot be resolved.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class TroubleshootingSolution {
    private final IssueType issueType;
    private final String issueDescription;
    private final List<Step> solutionSteps;
    private final boolean resolved;
    private final String escalationMessage;
    private final String supportContact;
    private final String helpArticleUrl;

    private TroubleshootingSolution(Builder builder) {
        this.issueType = builder.issueType;
        this.issueDescription = builder.issueDescription;
        this.solutionSteps = builder.solutionSteps != null ? List.copyOf(builder.solutionSteps) : List.of();
        this.resolved = builder.resolved;
        this.escalationMessage = builder.escalationMessage;
        this.supportContact = builder.supportContact;
        this.helpArticleUrl = builder.helpArticleUrl;
    }

    // Getters
    public IssueType getIssueType() { return issueType; }
    public String getIssueDescription() { return issueDescription; }
    public List<Step> getSolutionSteps() { return solutionSteps; }
    public boolean isResolved() { return resolved; }
    public String getEscalationMessage() { return escalationMessage; }
    public String getSupportContact() { return supportContact; }
    public String getHelpArticleUrl() { return helpArticleUrl; }

    /**
     * Format for voice output.
     */
    public String toVoiceFormat() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("Let me help you with that ");
        sb.append(issueType.getDisplayName().toLowerCase());
        sb.append(". ");
        
        if (solutionSteps.isEmpty()) {
            sb.append(escalationMessage != null ? escalationMessage : 
                    "I recommend contacting our support team for this issue.");
        } else {
            sb.append("Try these steps: ");
            for (Step step : solutionSteps) {
                sb.append(step.toVoiceFormat()).append(". ");
            }
            sb.append("Did that resolve your issue?");
        }
        
        return sb.toString();
    }

    /**
     * Create an escalation solution when troubleshooting cannot resolve the issue.
     */
    public TroubleshootingSolution escalate() {
        return TroubleshootingSolution.builder()
                .issueType(this.issueType)
                .issueDescription(this.issueDescription)
                .resolved(false)
                .escalationMessage("I wasn't able to resolve this issue. Would you like me to connect you with our technical support team?")
                .supportContact("+49 800 123-4567")
                .helpArticleUrl(this.helpArticleUrl)
                .build();
    }

    /**
     * Convert to Map for JSON serialization.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("issueType", issueType.name());
        map.put("issueDescription", issueDescription);
        map.put("solutionSteps", solutionSteps.stream().map(Step::toMap).collect(Collectors.toList()));
        map.put("resolved", resolved);
        map.put("stepCount", solutionSteps.size());
        if (escalationMessage != null) {
            map.put("escalationMessage", escalationMessage);
        }
        if (supportContact != null) {
            map.put("supportContact", supportContact);
        }
        if (helpArticleUrl != null) {
            map.put("helpArticleUrl", helpArticleUrl);
        }
        map.put("voiceResponse", toVoiceFormat());
        return map;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private IssueType issueType;
        private String issueDescription;
        private List<Step> solutionSteps = new ArrayList<>();
        private boolean resolved = true;
        private String escalationMessage;
        private String supportContact;
        private String helpArticleUrl;

        public Builder issueType(IssueType issueType) { this.issueType = issueType; return this; }
        public Builder issueDescription(String issueDescription) { this.issueDescription = issueDescription; return this; }
        public Builder solutionSteps(List<Step> solutionSteps) { this.solutionSteps = new ArrayList<>(solutionSteps); return this; }
        public Builder addStep(Step step) { this.solutionSteps.add(step); return this; }
        public Builder addStep(int number, String instruction) { 
            this.solutionSteps.add(Step.simple(number, instruction)); 
            return this; 
        }
        public Builder resolved(boolean resolved) { this.resolved = resolved; return this; }
        public Builder escalationMessage(String escalationMessage) { this.escalationMessage = escalationMessage; return this; }
        public Builder supportContact(String supportContact) { this.supportContact = supportContact; return this; }
        public Builder helpArticleUrl(String helpArticleUrl) { this.helpArticleUrl = helpArticleUrl; return this; }

        public TroubleshootingSolution build() {
            if (issueType == null) {
                issueType = IssueType.FEATURE;
            }
            return new TroubleshootingSolution(this);
        }
    }
}
