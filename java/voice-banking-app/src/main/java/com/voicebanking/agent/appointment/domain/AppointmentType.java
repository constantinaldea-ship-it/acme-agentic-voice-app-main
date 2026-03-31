package com.voicebanking.agent.appointment.domain;

/**
 * Types of appointments available for booking.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum AppointmentType {
    GENERAL_INQUIRY("General banking questions", 30, false),
    ACCOUNT_OPENING("New account setup", 45, true),
    MORTGAGE_CONSULTATION("Mortgage advisory", 60, true),
    INVESTMENT_ADVICE("Wealth management", 60, true),
    CREDIT_APPLICATION("Loan or credit application", 45, true),
    SAFE_DEPOSIT_BOX("Safe deposit access", 15, false),
    DOCUMENT_CERTIFICATION("Document services", 15, false);

    private final String description;
    private final int durationMinutes;
    private final boolean confirmationRequired;

    AppointmentType(String description, int durationMinutes, boolean confirmationRequired) {
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.confirmationRequired = confirmationRequired;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public boolean isConfirmationRequired() {
        return confirmationRequired;
    }

    /**
     * Check if this appointment type requires an advisor.
     */
    public boolean requiresAdvisor() {
        return this == MORTGAGE_CONSULTATION || this == INVESTMENT_ADVICE || this == CREDIT_APPLICATION;
    }

    /**
     * Check if this is a quick service appointment.
     */
    public boolean isQuickService() {
        return durationMinutes <= 15;
    }
}
