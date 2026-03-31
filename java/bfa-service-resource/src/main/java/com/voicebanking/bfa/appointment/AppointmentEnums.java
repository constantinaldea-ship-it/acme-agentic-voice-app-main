package com.voicebanking.bfa.appointment;

/**
 * Shared enum types for the advisory appointment domain.
 *
 * @author Codex
 * @since 2026-03-15
 */
public final class AppointmentEnums {

    private AppointmentEnums() {
    }

    public enum EntryPath {
        SERVICE_REQUEST,
        PRODUCT_CONSULTATION
    }

    public enum ConsultationChannel {
        BRANCH,
        PHONE,
        VIDEO;

        public boolean isRemote() {
            return this == PHONE || this == VIDEO;
        }
    }

    public enum AdvisorMode {
        INTERNAL,
        INDEPENDENT,
        PRIVATE_BANKING
    }

    public enum LocationType {
        BRANCH,
        REMOTE_CENTER
    }

    public enum FallbackSuggestionType {
        TRY_ANOTHER_DAY,
        TRY_ANOTHER_LOCATION,
        TRY_ANOTHER_CHANNEL,
        HANDOFF
    }

    public enum Salutation {
        FRAU,
        HERR
    }

    public enum AppointmentStatus {
        PENDING,
        CONFIRMED,
        CANCELLED,
        RESCHEDULED
    }

    public enum DeliveryChannel {
        NONE,
        EMAIL
    }
}
