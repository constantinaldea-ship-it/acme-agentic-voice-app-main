package com.voicebanking.agent.appointment.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Request object for booking a new appointment.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public record BookingRequest(
    String customerId,
    AppointmentType type,
    String branchId,
    String slotId,
    LocalDateTime requestedTime,
    String advisorPreference,
    String notes,
    String contactPreference
) {
    /**
     * Create a booking request from input map.
     */
    public static BookingRequest fromMap(Map<String, Object> input, String customerId) {
        String typeStr = (String) input.getOrDefault("type", "GENERAL_INQUIRY");
        AppointmentType type = AppointmentType.valueOf(typeStr);
        
        String branchId = (String) input.get("branchId");
        String slotId = (String) input.get("slotId");
        
        LocalDateTime requestedTime = null;
        Object timeObj = input.get("requestedTime");
        if (timeObj instanceof String timeStr) {
            requestedTime = LocalDateTime.parse(timeStr);
        } else if (timeObj instanceof LocalDateTime ldt) {
            requestedTime = ldt;
        }
        
        String advisorPreference = (String) input.get("advisorPreference");
        String notes = (String) input.get("notes");
        String contactPreference = (String) input.getOrDefault("contactPreference", "email");
        
        return new BookingRequest(
            customerId,
            type,
            branchId,
            slotId,
            requestedTime,
            advisorPreference,
            notes,
            contactPreference
        );
    }

    /**
     * Validate the booking request.
     */
    public ValidationResult validate() {
        if (customerId == null || customerId.isBlank()) {
            return ValidationResult.invalid("Customer ID is required");
        }
        if (type == null) {
            return ValidationResult.invalid("Appointment type is required");
        }
        if (branchId == null && slotId == null) {
            return ValidationResult.invalid("Either branch ID or slot ID is required");
        }
        if (requestedTime != null && requestedTime.isBefore(LocalDateTime.now())) {
            return ValidationResult.invalid("Cannot book appointments in the past");
        }
        if (requestedTime != null) {
            LocalDate requestedDate = requestedTime.toLocalDate();
            LocalDate maxDate = LocalDate.now().plusMonths(3);
            if (requestedDate.isAfter(maxDate)) {
                return ValidationResult.invalid("Cannot book appointments more than 3 months in advance");
            }
        }
        return ValidationResult.success();
    }

    /**
     * Convert to map.
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("customerId", customerId);
        map.put("type", type != null ? type.name() : null);
        map.put("branchId", branchId);
        map.put("slotId", slotId);
        map.put("requestedTime", requestedTime != null ? requestedTime.toString() : null);
        map.put("advisorPreference", advisorPreference);
        map.put("notes", notes);
        map.put("contactPreference", contactPreference);
        return map;
    }

    /**
     * Validation result record.
     */
    public record ValidationResult(boolean valid, String errorMessage) {
        public static ValidationResult success() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }

        public boolean isValid() {
            return valid;
        }
    }
}
