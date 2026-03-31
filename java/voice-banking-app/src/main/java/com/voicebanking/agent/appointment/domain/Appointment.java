package com.voicebanking.agent.appointment.domain;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents a customer appointment.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public class Appointment {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    private String appointmentId;
    private String customerId;
    private AppointmentType type;
    private AppointmentStatus status;
    private LocalDateTime scheduledTime;
    private int durationMinutes;
    private String branchId;
    private String branchName;
    private String advisorId;
    private String advisorName;
    private String notes;
    private String confirmationNumber;
    private Instant createdAt;
    private Instant updatedAt;

    private Appointment() {}

    // Getters
    public String getAppointmentId() { return appointmentId; }
    public String getCustomerId() { return customerId; }
    public AppointmentType getType() { return type; }
    public AppointmentStatus getStatus() { return status; }
    public LocalDateTime getScheduledTime() { return scheduledTime; }
    public int getDurationMinutes() { return durationMinutes; }
    public String getBranchId() { return branchId; }
    public String getBranchName() { return branchName; }
    public String getAdvisorId() { return advisorId; }
    public String getAdvisorName() { return advisorName; }
    public String getNotes() { return notes; }
    public String getConfirmationNumber() { return confirmationNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /**
     * Get the end time of the appointment.
     */
    public LocalDateTime getEndTime() {
        return scheduledTime.plusMinutes(durationMinutes);
    }

    /**
     * Check if the appointment can be modified (at least 2 hours before).
     */
    public boolean canModify() {
        if (!status.canModify()) {
            return false;
        }
        LocalDateTime cutoff = scheduledTime.minusHours(2);
        return LocalDateTime.now().isBefore(cutoff);
    }

    /**
     * Check if the appointment can be cancelled (at least 2 hours before).
     */
    public boolean canCancel() {
        if (!status.canCancel()) {
            return false;
        }
        LocalDateTime cutoff = scheduledTime.minusHours(2);
        return LocalDateTime.now().isBefore(cutoff);
    }

    /**
     * Format as a voice-friendly string.
     */
    public String toVoiceFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append(type.getDescription());
        sb.append(" on ");
        sb.append(scheduledTime.format(DATE_FORMATTER));
        sb.append(" at ");
        sb.append(scheduledTime.format(TIME_FORMATTER));
        
        if (branchName != null && !branchName.isBlank()) {
            sb.append(" at the ");
            sb.append(branchName);
        }
        
        if (advisorName != null && !advisorName.isBlank()) {
            sb.append(" with ");
            sb.append(advisorName);
        }
        
        return sb.toString();
    }

    /**
     * Convert to map for API response.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("appointmentId", appointmentId);
        map.put("customerId", customerId);
        map.put("type", type.name());
        map.put("typeDescription", type.getDescription());
        map.put("status", status.name());
        map.put("statusDescription", status.getDescription());
        map.put("scheduledTime", scheduledTime.toString());
        map.put("endTime", getEndTime().toString());
        map.put("durationMinutes", durationMinutes);
        map.put("branchId", branchId);
        map.put("branchName", branchName);
        map.put("advisorId", advisorId);
        map.put("advisorName", advisorName);
        map.put("notes", notes);
        map.put("confirmationNumber", confirmationNumber);
        map.put("canModify", canModify());
        map.put("canCancel", canCancel());
        map.put("voiceFormat", toVoiceFormat());
        map.put("createdAt", createdAt != null ? createdAt.toString() : null);
        map.put("updatedAt", updatedAt != null ? updatedAt.toString() : null);
        return map;
    }

    // Builder pattern
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Appointment apt = new Appointment();

        public Builder appointmentId(String appointmentId) {
            apt.appointmentId = appointmentId;
            return this;
        }

        public Builder customerId(String customerId) {
            apt.customerId = customerId;
            return this;
        }

        public Builder type(AppointmentType type) {
            apt.type = type;
            apt.durationMinutes = type.getDurationMinutes();
            return this;
        }

        public Builder status(AppointmentStatus status) {
            apt.status = status;
            return this;
        }

        public Builder scheduledTime(LocalDateTime scheduledTime) {
            apt.scheduledTime = scheduledTime;
            return this;
        }

        public Builder durationMinutes(int durationMinutes) {
            apt.durationMinutes = durationMinutes;
            return this;
        }

        public Builder branchId(String branchId) {
            apt.branchId = branchId;
            return this;
        }

        public Builder branchName(String branchName) {
            apt.branchName = branchName;
            return this;
        }

        public Builder advisorId(String advisorId) {
            apt.advisorId = advisorId;
            return this;
        }

        public Builder advisorName(String advisorName) {
            apt.advisorName = advisorName;
            return this;
        }

        public Builder notes(String notes) {
            apt.notes = notes;
            return this;
        }

        public Builder confirmationNumber(String confirmationNumber) {
            apt.confirmationNumber = confirmationNumber;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            apt.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            apt.updatedAt = updatedAt;
            return this;
        }

        public Appointment build() {
            if (apt.appointmentId == null) {
                apt.appointmentId = "APT-" + System.currentTimeMillis();
            }
            if (apt.status == null) {
                apt.status = AppointmentStatus.PENDING;
            }
            if (apt.createdAt == null) {
                apt.createdAt = Instant.now();
            }
            apt.updatedAt = Instant.now();
            if (apt.confirmationNumber == null) {
                apt.confirmationNumber = "CONF-" + apt.appointmentId.hashCode();
            }
            return apt;
        }
    }
}
