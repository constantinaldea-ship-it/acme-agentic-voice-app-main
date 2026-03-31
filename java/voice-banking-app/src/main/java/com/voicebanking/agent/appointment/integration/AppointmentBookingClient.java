package com.voicebanking.agent.appointment.integration;

import com.voicebanking.agent.appointment.domain.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Interface for Appointment Booking API (I-20) integration.
 * Production implementations would connect to real appointment booking systems.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
public interface AppointmentBookingClient {

    /**
     * Get appointments for a customer.
     * 
     * @param customerId The customer identifier
     * @param status Filter by status (null for all)
     * @return List of appointments
     */
    List<Appointment> getAppointments(String customerId, AppointmentStatus status);

    /**
     * Get a specific appointment by ID.
     * 
     * @param appointmentId The appointment identifier
     * @return The appointment, or null if not found
     */
    Appointment getAppointment(String appointmentId);

    /**
     * Check available time slots.
     * 
     * @param branchId The branch identifier (optional)
     * @param type The appointment type
     * @param startDate Start of date range
     * @param endDate End of date range
     * @return List of available time slots
     */
    List<TimeSlot> getAvailability(String branchId, AppointmentType type, 
                                    LocalDate startDate, LocalDate endDate);

    /**
     * Get the next available slots for an appointment type.
     * 
     * @param branchId The branch identifier (optional)
     * @param type The appointment type
     * @param limit Maximum number of slots to return
     * @return List of next available time slots
     */
    List<TimeSlot> getNextAvailable(String branchId, AppointmentType type, int limit);

    /**
     * Book a new appointment.
     * 
     * @param request The booking request
     * @return The created appointment
     */
    Appointment createAppointment(BookingRequest request);

    /**
     * Modify an existing appointment.
     * 
     * @param appointmentId The appointment to modify
     * @param newSlotId The new time slot
     * @return The updated appointment
     */
    Appointment modifyAppointment(String appointmentId, String newSlotId);

    /**
     * Cancel an appointment.
     * 
     * @param appointmentId The appointment to cancel
     * @param reason Optional cancellation reason
     * @return true if cancellation was successful
     */
    boolean cancelAppointment(String appointmentId, String reason);

    /**
     * Check if a specific slot is still available.
     * 
     * @param slotId The slot identifier
     * @return true if the slot is available
     */
    boolean isSlotAvailable(String slotId);
}
