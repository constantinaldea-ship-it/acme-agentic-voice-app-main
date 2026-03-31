package com.voicebanking.agent.appointment.service;

import com.voicebanking.agent.appointment.domain.*;
import com.voicebanking.agent.appointment.integration.AppointmentBookingClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for checking appointment availability and slot management.
 * Provides business logic for finding available appointment slots.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Service
public class AvailabilityService {
    private static final Logger log = LoggerFactory.getLogger(AvailabilityService.class);

    private static final int DEFAULT_LOOKAHEAD_DAYS = 14;
    private static final int MAX_LOOKAHEAD_DAYS = 90;
    private static final int DEFAULT_RESULTS_LIMIT = 5;

    private final AppointmentBookingClient bookingClient;

    public AvailabilityService(AppointmentBookingClient bookingClient) {
        this.bookingClient = bookingClient;
    }

    /**
     * Check availability for a specific branch and appointment type.
     * 
     * @param branchId Branch identifier (optional - null for all branches)
     * @param type Appointment type
     * @param preferredDate Preferred date (optional - null for next available)
     * @param preferredTimeOfDay Preferred time: "morning", "afternoon", "any" (optional)
     * @return AvailabilityResult with available slots
     */
    public AvailabilityResult checkAvailability(String branchId, AppointmentType type,
                                                  LocalDate preferredDate, String preferredTimeOfDay) {
        log.info("Checking availability: branch={}, type={}, date={}, time={}", 
            branchId, type, preferredDate, preferredTimeOfDay);

        LocalDate startDate = preferredDate != null ? preferredDate : LocalDate.now().plusDays(1);
        LocalDate endDate = startDate.plusDays(DEFAULT_LOOKAHEAD_DAYS);

        // Ensure we don't look too far ahead
        LocalDate maxDate = LocalDate.now().plusDays(MAX_LOOKAHEAD_DAYS);
        if (endDate.isAfter(maxDate)) {
            endDate = maxDate;
        }

        List<TimeSlot> slots = bookingClient.getAvailability(branchId, type, startDate, endDate);

        // Filter by time of day preference
        if (preferredTimeOfDay != null && !preferredTimeOfDay.equalsIgnoreCase("any")) {
            slots = filterByTimeOfDay(slots, preferredTimeOfDay);
        }

        // Sort and limit results
        slots = slots.stream()
            .sorted(Comparator.comparing(TimeSlot::startTime))
            .limit(DEFAULT_RESULTS_LIMIT)
            .collect(Collectors.toList());

        return new AvailabilityResult(
            !slots.isEmpty(),
            slots,
            type,
            branchId,
            generateAvailabilityMessage(slots, type, preferredDate)
        );
    }

    /**
     * Get next available slots quickly.
     * 
     * @param branchId Branch identifier (optional)
     * @param type Appointment type
     * @param limit Maximum number of slots to return
     * @return List of next available slots
     */
    public List<TimeSlot> getNextAvailable(String branchId, AppointmentType type, int limit) {
        return bookingClient.getNextAvailable(branchId, type, limit);
    }

    /**
     * Check if a specific slot is still available.
     * 
     * @param slotId Slot identifier
     * @return true if available
     */
    public boolean isSlotAvailable(String slotId) {
        return bookingClient.isSlotAvailable(slotId);
    }

    /**
     * Find slots for a specific week.
     */
    public List<TimeSlot> getSlotsForWeek(String branchId, AppointmentType type, LocalDate weekStart) {
        LocalDate monday = weekStart.with(DayOfWeek.MONDAY);
        LocalDate friday = monday.plusDays(4);
        
        return bookingClient.getAvailability(branchId, type, monday, friday);
    }

    /**
     * Filter slots by time of day.
     */
    private List<TimeSlot> filterByTimeOfDay(List<TimeSlot> slots, String timeOfDay) {
        LocalTime morningEnd = LocalTime.of(12, 0);
        LocalTime afternoonEnd = LocalTime.of(17, 0);

        return slots.stream()
            .filter(slot -> {
                LocalTime slotTime = slot.getStartTimeOnly();
                return switch (timeOfDay.toLowerCase()) {
                    case "morning" -> slotTime.isBefore(morningEnd);
                    case "afternoon" -> !slotTime.isBefore(morningEnd) && slotTime.isBefore(afternoonEnd);
                    case "evening" -> !slotTime.isBefore(afternoonEnd);
                    default -> true;
                };
            })
            .collect(Collectors.toList());
    }

    /**
     * Generate a voice-friendly availability message.
     */
    private String generateAvailabilityMessage(List<TimeSlot> slots, AppointmentType type, 
                                                LocalDate preferredDate) {
        if (slots.isEmpty()) {
            if (preferredDate != null) {
                return String.format(
                    "I'm sorry, there are no available slots for %s on %s. Would you like me to check other dates?",
                    type.getDescription().toLowerCase(),
                    CalendarFormatter.formatDateNatural(preferredDate)
                );
            }
            return String.format(
                "I'm sorry, there are no available slots for %s in the next two weeks. Would you like me to check further ahead?",
                type.getDescription().toLowerCase()
            );
        }

        StringBuilder sb = new StringBuilder();
        if (slots.size() == 1) {
            TimeSlot slot = slots.get(0);
            sb.append(String.format("I found one available slot for %s: %s.",
                type.getDescription().toLowerCase(),
                slot.toVoiceFormatWithLocation()
            ));
        } else {
            sb.append(String.format("I found %d available slots for %s. ", 
                slots.size(), type.getDescription().toLowerCase()));
            
            for (int i = 0; i < slots.size(); i++) {
                TimeSlot slot = slots.get(i);
                if (i == 0) {
                    sb.append("The first is ");
                } else if (i == slots.size() - 1) {
                    sb.append("Or ");
                } else {
                    sb.append("The second is ");
                }
                sb.append(slot.toVoiceFormat());
                if (i < slots.size() - 1) {
                    sb.append(". ");
                } else {
                    sb.append(".");
                }
            }
        }

        sb.append(" Which works best for you?");
        return sb.toString();
    }

    /**
     * Result record for availability check.
     */
    public record AvailabilityResult(
        boolean hasAvailability,
        List<TimeSlot> slots,
        AppointmentType type,
        String branchId,
        String voiceMessage
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("hasAvailability", hasAvailability);
            map.put("slotCount", slots.size());
            map.put("slots", slots.stream().map(TimeSlot::toMap).collect(Collectors.toList()));
            map.put("type", type.name());
            map.put("branchId", branchId);
            map.put("voiceMessage", voiceMessage);
            return map;
        }
    }
}
