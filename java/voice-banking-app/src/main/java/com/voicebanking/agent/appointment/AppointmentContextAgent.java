package com.voicebanking.agent.appointment;

import com.voicebanking.agent.Agent;
import com.voicebanking.agent.appointment.domain.*;
import com.voicebanking.agent.appointment.integration.AppointmentBookingClient;
import com.voicebanking.agent.appointment.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AppointmentContextAgent — Branch Appointments & Advisor Scheduling
 * 
 * This agent manages branch appointments and advisor scheduling. It integrates with
 * the Appointment Booking API (I-20) to view existing appointments, check availability,
 * and request new appointments.
 * 
 * Category: Category 4 — Daily Banking Execution (Read+Write)
 * 
 * @author Augment Agent
 * @since 2026-01-25
 * @see AGENT-007 Implementation Plan (repository path: ces-agent/docs/advisory-agent/implementation-plan/AGENT-007-appointment-context.md)
 */
@Component
public class AppointmentContextAgent implements Agent {
    private static final Logger log = LoggerFactory.getLogger(AppointmentContextAgent.class);

    private static final String AGENT_ID = "appointment-context";
    private static final List<String> TOOL_IDS = List.of(
            "getMyAppointments",
            "checkAvailability",
            "requestAppointment",
            "modifyAppointment",
            "cancelAppointment"
    );

    private final AppointmentBookingClient bookingClient;
    private final AvailabilityService availabilityService;
    private final AppointmentLogService logService;

    public AppointmentContextAgent(
            AppointmentBookingClient bookingClient,
            AvailabilityService availabilityService,
            AppointmentLogService logService) {
        this.bookingClient = bookingClient;
        this.availabilityService = availabilityService;
        this.logService = logService;
        log.info("AppointmentContextAgent initialized with {} tools", TOOL_IDS.size());
    }

    @Override
    public String getAgentId() {
        return AGENT_ID;
    }

    @Override
    public String getDescription() {
        return "Manages branch appointments and advisor scheduling. Provides tools for viewing appointments, " +
               "checking availability, booking new appointments, and modifying or cancelling existing ones.";
    }

    @Override
    public List<String> getToolIds() {
        return TOOL_IDS;
    }

    @Override
    public Map<String, Object> executeTool(String toolId, Map<String, Object> input) {
        log.debug("Executing tool: {} with input: {}", toolId, input);

        try {
            return switch (toolId) {
                case "getMyAppointments" -> getMyAppointments(input);
                case "checkAvailability" -> checkAvailability(input);
                case "requestAppointment" -> requestAppointment(input);
                case "modifyAppointment" -> modifyAppointment(input);
                case "cancelAppointment" -> cancelAppointment(input);
                default -> Map.of("error", "Unknown tool: " + toolId, "success", false);
            };
        } catch (IllegalArgumentException e) {
            log.warn("Validation error in tool {}: {}", toolId, e.getMessage());
            return Map.of("error", e.getMessage(), "success", false);
        } catch (IllegalStateException e) {
            log.warn("State error in tool {}: {}", toolId, e.getMessage());
            return Map.of("error", e.getMessage(), "success", false);
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolId, e.getMessage(), e);
            return Map.of("error", "An error occurred: " + e.getMessage(), "success", false);
        }
    }

    /**
     * Get customer's appointments.
     * 
     * Input:
     * - customerId: Customer identifier (required)
     * - status: Filter by status - "UPCOMING", "PAST", "CANCELLED", or null for all
     * 
     * Output:
     * - appointments: List of appointment details
     * - count: Number of appointments
     * - voiceMessage: Voice-friendly summary
     */
    private Map<String, Object> getMyAppointments(Map<String, Object> input) {
        String customerId = (String) input.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            customerId = "CUST-123"; // Default for demo
        }

        String statusStr = (String) input.get("status");
        AppointmentStatus status = null;
        boolean upcoming = false;

        if ("UPCOMING".equalsIgnoreCase(statusStr)) {
            status = AppointmentStatus.CONFIRMED;
            upcoming = true;
        } else if ("PAST".equalsIgnoreCase(statusStr)) {
            status = AppointmentStatus.COMPLETED;
        } else if (statusStr != null && !statusStr.isBlank()) {
            try {
                status = AppointmentStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                // Ignore, will return all
            }
        }

        List<Appointment> appointments = bookingClient.getAppointments(customerId, status);
        
        // Filter for upcoming if requested
        if (upcoming) {
            appointments = appointments.stream()
                .filter(apt -> apt.getStatus().isActive())
                .filter(apt -> apt.getScheduledTime().isAfter(java.time.LocalDateTime.now()))
                .collect(Collectors.toList());
        }

        logService.logAppointmentQuery(customerId, status, appointments.size());

        // Generate voice message
        String voiceMessage = generateAppointmentsVoiceMessage(appointments, statusStr);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("appointments", appointments.stream().map(Appointment::toMap).collect(Collectors.toList()));
        result.put("count", appointments.size());
        result.put("voiceMessage", voiceMessage);
        return result;
    }

    /**
     * Check available appointment slots.
     * 
     * Input:
     * - type: Appointment type (required) - e.g., "MORTGAGE_CONSULTATION"
     * - branchId: Branch identifier (optional)
     * - preferredDate: Preferred date (optional) - ISO format or natural language
     * - preferredTime: Preferred time of day - "morning", "afternoon", "any"
     * - week: Week reference - "this week", "next week" (optional)
     * 
     * Output:
     * - slots: List of available time slots
     * - hasAvailability: Boolean indicating if slots are available
     * - voiceMessage: Voice-friendly availability summary
     */
    private Map<String, Object> checkAvailability(Map<String, Object> input) {
        String typeStr = (String) input.getOrDefault("type", "GENERAL_INQUIRY");
        AppointmentType type = AppointmentType.valueOf(typeStr.toUpperCase());

        String branchId = (String) input.get("branchId");
        String preferredTime = (String) input.getOrDefault("preferredTime", "any");

        // Parse date - support natural language
        LocalDate preferredDate = null;
        String dateInput = (String) input.get("preferredDate");
        String weekInput = (String) input.get("week");

        if (dateInput != null) {
            preferredDate = CalendarFormatter.parseNaturalDate(dateInput);
            if (preferredDate == null) {
                try {
                    preferredDate = LocalDate.parse(dateInput);
                } catch (Exception e) {
                    log.debug("Could not parse date: {}", dateInput);
                }
            }
        } else if (weekInput != null) {
            preferredDate = CalendarFormatter.parseNaturalDate(weekInput);
        }

        AvailabilityService.AvailabilityResult result = 
            availabilityService.checkAvailability(branchId, type, preferredDate, preferredTime);

        logService.logAvailabilityCheck("CUST-123", type, branchId, result.slots().size());

        Map<String, Object> response = new HashMap<>(result.toMap());
        response.put("success", true);
        return response;
    }

    /**
     * Request/book a new appointment.
     * 
     * Input:
     * - customerId: Customer identifier (required)
     * - type: Appointment type (required)
     * - slotId: Selected slot ID from checkAvailability (required if not using requestedTime)
     * - branchId: Branch identifier (required if not using slotId)
     * - requestedTime: Requested datetime (optional alternative to slotId)
     * - notes: Additional notes (optional)
     * - contactPreference: How to send confirmation - "email", "sms", "both" (optional)
     * 
     * Output:
     * - appointment: Created appointment details
     * - confirmationNumber: Confirmation number
     * - voiceMessage: Voice-friendly confirmation
     */
    private Map<String, Object> requestAppointment(Map<String, Object> input) {
        String customerId = (String) input.get("customerId");
        if (customerId == null || customerId.isBlank()) {
            customerId = "CUST-123"; // Default for demo
        }

        BookingRequest request = BookingRequest.fromMap(input, customerId);
        
        // Validate request
        BookingRequest.ValidationResult validation = request.validate();
        if (!validation.valid()) {
            return Map.of(
                "success", false,
                "error", validation.errorMessage(),
                "voiceMessage", "I couldn't book the appointment. " + validation.errorMessage()
            );
        }

        // Check slot availability one more time
        if (request.slotId() != null && !availabilityService.isSlotAvailable(request.slotId())) {
            return Map.of(
                "success", false,
                "error", "The selected time slot is no longer available",
                "voiceMessage", "I'm sorry, that time slot was just taken. Would you like me to check other available times?"
            );
        }

        Appointment appointment = bookingClient.createAppointment(request);
        logService.logAppointmentCreated(customerId, appointment);

        // Generate confirmation message
        String voiceMessage = generateBookingConfirmationMessage(appointment);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("appointment", appointment.toMap());
        result.put("confirmationNumber", appointment.getConfirmationNumber());
        result.put("voiceMessage", voiceMessage);
        return result;
    }

    /**
     * Modify an existing appointment (reschedule).
     * 
     * Input:
     * - appointmentId: Appointment to modify (required)
     * - newSlotId: New time slot ID (required)
     * 
     * Output:
     * - appointment: Updated appointment details
     * - voiceMessage: Voice-friendly confirmation
     */
    private Map<String, Object> modifyAppointment(Map<String, Object> input) {
        String appointmentId = (String) input.get("appointmentId");
        String newSlotId = (String) input.get("newSlotId");

        if (appointmentId == null || appointmentId.isBlank()) {
            return Map.of(
                "success", false,
                "error", "Appointment ID is required",
                "voiceMessage", "I need to know which appointment you'd like to modify. Could you tell me the appointment ID or describe which one?"
            );
        }

        if (newSlotId == null || newSlotId.isBlank()) {
            return Map.of(
                "success", false,
                "error", "New slot ID is required",
                "voiceMessage", "Please select a new time slot for your appointment. Would you like me to check availability?"
            );
        }

        // Get existing appointment for logging
        Appointment existing = bookingClient.getAppointment(appointmentId);
        if (existing == null) {
            return Map.of(
                "success", false,
                "error", "Appointment not found: " + appointmentId,
                "voiceMessage", "I couldn't find that appointment. Could you check the appointment ID?"
            );
        }

        if (!existing.canModify()) {
            return Map.of(
                "success", false,
                "error", "Appointment cannot be modified (less than 2 hours before scheduled time)",
                "voiceMessage", "I'm sorry, this appointment cannot be rescheduled because it's less than 2 hours away. Would you like to cancel it instead?"
            );
        }

        String oldSlot = existing.getScheduledTime().toString();
        Appointment updated = bookingClient.modifyAppointment(appointmentId, newSlotId);
        logService.logAppointmentModified(existing.getCustomerId(), appointmentId, oldSlot, newSlotId);

        String voiceMessage = String.format(
            "I've rescheduled your appointment from %s to %s. You'll receive an updated confirmation.",
            CalendarFormatter.formatDateTimeNatural(existing.getScheduledTime()),
            CalendarFormatter.formatDateTimeNatural(updated.getScheduledTime())
        );

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("appointment", updated.toMap());
        result.put("voiceMessage", voiceMessage);
        return result;
    }

    /**
     * Cancel an existing appointment.
     * 
     * Input:
     * - appointmentId: Appointment to cancel (required)
     * - reason: Cancellation reason (optional)
     * 
     * Output:
     * - status: "CANCELLED" if successful
     * - voiceMessage: Voice-friendly confirmation
     */
    private Map<String, Object> cancelAppointment(Map<String, Object> input) {
        String appointmentId = (String) input.get("appointmentId");
        String reason = (String) input.get("reason");

        if (appointmentId == null || appointmentId.isBlank()) {
            return Map.of(
                "success", false,
                "error", "Appointment ID is required",
                "voiceMessage", "I need to know which appointment you'd like to cancel. Could you tell me the appointment ID or describe which one?"
            );
        }

        // Get existing appointment for confirmation message
        Appointment existing = bookingClient.getAppointment(appointmentId);
        if (existing == null) {
            return Map.of(
                "success", false,
                "error", "Appointment not found: " + appointmentId,
                "voiceMessage", "I couldn't find that appointment. Could you check the appointment ID?"
            );
        }

        if (!existing.canCancel()) {
            return Map.of(
                "success", false,
                "error", "Appointment cannot be cancelled (less than 2 hours before scheduled time or already cancelled)",
                "voiceMessage", "I'm sorry, this appointment cannot be cancelled because it's less than 2 hours away or has already been cancelled."
            );
        }

        boolean cancelled = bookingClient.cancelAppointment(appointmentId, reason);
        
        if (cancelled) {
            logService.logAppointmentCancelled(existing.getCustomerId(), appointmentId, reason);
            
            String voiceMessage = String.format(
                "Your %s appointment for %s has been cancelled. Would you like to schedule a new appointment?",
                existing.getType().getDescription().toLowerCase(),
                CalendarFormatter.formatDateTimeNatural(existing.getScheduledTime())
            );

            return Map.of(
                "success", true,
                "status", "CANCELLED",
                "appointmentId", appointmentId,
                "voiceMessage", voiceMessage
            );
        } else {
            return Map.of(
                "success", false,
                "error", "Failed to cancel appointment",
                "voiceMessage", "I'm sorry, I couldn't cancel that appointment. Please try again or contact us for assistance."
            );
        }
    }

    /**
     * Generate voice message for appointments list.
     */
    private String generateAppointmentsVoiceMessage(List<Appointment> appointments, String statusFilter) {
        if (appointments.isEmpty()) {
            if ("UPCOMING".equalsIgnoreCase(statusFilter)) {
                return "You don't have any upcoming appointments. Would you like to schedule one?";
            }
            return "I couldn't find any appointments matching your request.";
        }

        if (appointments.size() == 1) {
            Appointment apt = appointments.get(0);
            return String.format("You have one appointment: %s.", apt.toVoiceFormat());
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("You have %d appointments. ", appointments.size()));
        
        for (int i = 0; i < Math.min(appointments.size(), 3); i++) {
            Appointment apt = appointments.get(i);
            if (i == 0) {
                sb.append("The first is ");
            } else if (i == 1) {
                sb.append("The second is ");
            } else {
                sb.append("And the third is ");
            }
            sb.append(apt.toVoiceFormat());
            sb.append(". ");
        }

        if (appointments.size() > 3) {
            sb.append(String.format("You have %d more appointments. Would you like to hear them?", 
                appointments.size() - 3));
        } else {
            sb.append("Would you like details on any of these?");
        }

        return sb.toString();
    }

    /**
     * Generate booking confirmation voice message.
     */
    private String generateBookingConfirmationMessage(Appointment appointment) {
        StringBuilder sb = new StringBuilder();
        sb.append("I've scheduled your ");
        sb.append(appointment.getType().getDescription().toLowerCase());
        sb.append(" for ");
        sb.append(CalendarFormatter.formatDateTimeNatural(appointment.getScheduledTime()));
        
        if (appointment.getBranchName() != null) {
            sb.append(" at the ");
            sb.append(appointment.getBranchName());
        }
        
        if (appointment.getAdvisorName() != null) {
            sb.append(" with ");
            sb.append(appointment.getAdvisorName());
        }
        
        sb.append(". Your confirmation number is ");
        sb.append(appointment.getConfirmationNumber());
        sb.append(". You'll receive a confirmation email shortly.");

        if (appointment.getType().isConfirmationRequired()) {
            sb.append(" Please note this appointment requires confirmation.");
        }

        return sb.toString();
    }
}
