package com.voicebanking.agent.handover.domain;

public enum HandoverReason {
    USER_REQUEST("User requested human assistance"),
    LOW_CONFIDENCE("AI confidence below threshold"),
    POLICY_ESCALATE("Policy requires human intervention"),
    ERROR("System error during processing"),
    TIMEOUT("User timeout in sensitive flow");

    private final String description;

    HandoverReason(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    public boolean isHighPriority() {
        return this == USER_REQUEST || this == ERROR;
    }

    public boolean isUserInitiated() {
        return this == USER_REQUEST;
    }
}
