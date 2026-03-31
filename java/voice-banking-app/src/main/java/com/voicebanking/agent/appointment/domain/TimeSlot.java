package com.voicebanking.agent.appointment.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an available time slot for booking an appointment.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public record TimeSlot(
    String slotId,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String branchId,
    String branchName,
    String advisorId,
    String advisorName,
    AppointmentType appointmentType,
    boolean available
) {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEEE, MMMM d");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm a");

    /**
     * Create a time slot with default availability.
     */
    public static TimeSlot create(String slotId, LocalDateTime startTime, int durationMinutes,
                                   String branchId, String branchName, 
                                   String advisorId, String advisorName,
                                   AppointmentType appointmentType) {
        return new TimeSlot(
            slotId,
            startTime,
            startTime.plusMinutes(durationMinutes),
            branchId,
            branchName,
            advisorId,
            advisorName,
            appointmentType,
            true
        );
    }

    /**
     * Get the date of this slot.
     */
    public LocalDate getDate() {
        return startTime.toLocalDate();
    }

    /**
     * Get the start time only.
     */
    public LocalTime getStartTimeOnly() {
        return startTime.toLocalTime();
    }

    /**
     * Get duration in minutes.
     */
    public int getDurationMinutes() {
        return (int) java.time.Duration.between(startTime, endTime).toMinutes();
    }

    /**
     * Format as a human-readable string for voice response.
     */
    public String toVoiceFormat() {
        return String.format("%s at %s", 
            startTime.format(DATE_FORMATTER),
            startTime.format(TIME_FORMATTER));
    }

    /**
     * Format with location for voice response.
     */
    public String toVoiceFormatWithLocation() {
        String base = toVoiceFormat();
        if (advisorName != null && !advisorName.isBlank()) {
            return String.format("%s at the %s with %s", base, branchName, advisorName);
        }
        return String.format("%s at the %s", base, branchName);
    }

    /**
     * Convert to map for API response.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("slotId", slotId);
        map.put("startTime", startTime.toString());
        map.put("endTime", endTime.toString());
        map.put("branchId", branchId);
        map.put("branchName", branchName);
        map.put("advisorId", advisorId);
        map.put("advisorName", advisorName);
        map.put("appointmentType", appointmentType != null ? appointmentType.name() : null);
        map.put("durationMinutes", getDurationMinutes());
        map.put("available", available);
        map.put("voiceFormat", toVoiceFormat());
        return map;
    }
}
