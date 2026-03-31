package com.voicebanking.agent.appointment.service;

import com.voicebanking.agent.appointment.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for logging appointment operations for audit and analytics.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class AppointmentLogService {
    private static final Logger log = LoggerFactory.getLogger(AppointmentLogService.class);
    private static final Logger auditLog = LoggerFactory.getLogger("audit.appointments");

    /**
     * Log appointment creation.
     */
    public void logAppointmentCreated(String customerId, Appointment appointment) {
        auditLog.info("APPOINTMENT_CREATED | customer={} | appointmentId={} | type={} | scheduledTime={} | branch={}",
            maskCustomerId(customerId),
            appointment.getAppointmentId(),
            appointment.getType(),
            appointment.getScheduledTime(),
            appointment.getBranchId()
        );
        log.debug("Appointment created: {} for customer {}", 
            appointment.getAppointmentId(), maskCustomerId(customerId));
    }

    /**
     * Log appointment modification.
     */
    public void logAppointmentModified(String customerId, String appointmentId, 
                                        String oldSlot, String newSlot) {
        auditLog.info("APPOINTMENT_MODIFIED | customer={} | appointmentId={} | oldSlot={} | newSlot={}",
            maskCustomerId(customerId),
            appointmentId,
            oldSlot,
            newSlot
        );
        log.debug("Appointment modified: {} from {} to {}", appointmentId, oldSlot, newSlot);
    }

    /**
     * Log appointment cancellation.
     */
    public void logAppointmentCancelled(String customerId, String appointmentId, String reason) {
        auditLog.info("APPOINTMENT_CANCELLED | customer={} | appointmentId={} | reason={}",
            maskCustomerId(customerId),
            appointmentId,
            reason != null ? reason : "Not specified"
        );
        log.debug("Appointment cancelled: {}", appointmentId);
    }

    /**
     * Log availability check.
     */
    public void logAvailabilityCheck(String customerId, AppointmentType type, 
                                      String branchId, int slotsFound) {
        log.debug("AVAILABILITY_CHECK | customer={} | type={} | branch={} | slotsFound={}",
            maskCustomerId(customerId),
            type,
            branchId,
            slotsFound
        );
    }

    /**
     * Log appointment query.
     */
    public void logAppointmentQuery(String customerId, AppointmentStatus status, int resultsFound) {
        log.debug("APPOINTMENT_QUERY | customer={} | status={} | resultsFound={}",
            maskCustomerId(customerId),
            status,
            resultsFound
        );
    }

    /**
     * Mask customer ID for logging (keep first 4 and last 2 characters).
     */
    private String maskCustomerId(String customerId) {
        if (customerId == null || customerId.length() <= 6) {
            return "****";
        }
        return customerId.substring(0, 4) + "****" + customerId.substring(customerId.length() - 2);
    }
}
