package com.voicebanking.agent.appointment.domain;

/**
 * Status of an appointment in its lifecycle.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public enum AppointmentStatus {
    PENDING("Awaiting confirmation"),
    CONFIRMED("Appointment confirmed"),
    CANCELLED("Appointment cancelled"),
    COMPLETED("Appointment completed"),
    NO_SHOW("Customer did not attend"),
    RESCHEDULED("Appointment rescheduled");

    private final String description;

    AppointmentStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Check if this status represents an active/upcoming appointment.
     */
    public boolean isActive() {
        return this == PENDING || this == CONFIRMED;
    }

    /**
     * Check if this status allows modification.
     */
    public boolean canModify() {
        return this == PENDING || this == CONFIRMED;
    }

    /**
     * Check if this status allows cancellation.
     */
    public boolean canCancel() {
        return this == PENDING || this == CONFIRMED;
    }
}
