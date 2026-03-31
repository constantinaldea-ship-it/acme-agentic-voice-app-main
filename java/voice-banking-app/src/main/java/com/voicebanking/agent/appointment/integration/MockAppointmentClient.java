package com.voicebanking.agent.appointment.integration;

import com.voicebanking.agent.appointment.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Mock implementation of AppointmentBookingClient for development and testing.
 * Provides realistic mock data for appointment scheduling scenarios.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@Component
@Profile("!production")
public class MockAppointmentClient implements AppointmentBookingClient {
    private static final Logger log = LoggerFactory.getLogger(MockAppointmentClient.class);

    private static final LocalTime BUSINESS_START = LocalTime.of(9, 0);
    private static final LocalTime BUSINESS_END = LocalTime.of(17, 0);
    private static final ZoneId TIMEZONE = ZoneId.of("Europe/Berlin");

    private final Map<String, Appointment> appointments = new ConcurrentHashMap<>();
    private final Map<String, TimeSlot> slots = new ConcurrentHashMap<>();
    private final Set<String> bookedSlots = ConcurrentHashMap.newKeySet();
    private final AtomicLong appointmentCounter = new AtomicLong(1000);
    private final AtomicLong slotCounter = new AtomicLong(1);

    // Mock branch data
    private static final List<Branch> BRANCHES = List.of(
        new Branch("BR-001", "Munich Central", "Marienplatz 1, 80331 Munich"),
        new Branch("BR-002", "Frankfurt Main", "Zeil 100, 60313 Frankfurt"),
        new Branch("BR-003", "Berlin Mitte", "Unter den Linden 77, 10117 Berlin")
    );

    // Mock advisor data
    private static final List<Advisor> ADVISORS = List.of(
        new Advisor("ADV-001", "Hans Müller", Set.of(AppointmentType.MORTGAGE_CONSULTATION, AppointmentType.INVESTMENT_ADVICE)),
        new Advisor("ADV-002", "Anna Schmidt", Set.of(AppointmentType.CREDIT_APPLICATION, AppointmentType.ACCOUNT_OPENING)),
        new Advisor("ADV-003", "Thomas Weber", Set.of(AppointmentType.INVESTMENT_ADVICE)),
        new Advisor("ADV-004", "Maria Fischer", Set.of(AppointmentType.GENERAL_INQUIRY, AppointmentType.ACCOUNT_OPENING)),
        new Advisor("ADV-005", "Klaus Becker", Set.of(AppointmentType.MORTGAGE_CONSULTATION, AppointmentType.CREDIT_APPLICATION))
    );

    public MockAppointmentClient() {
        initializeMockData();
    }

    private void initializeMockData() {
        // Create some mock appointments for demo customer
        String customerId = "CUST-123";
        LocalDateTime now = LocalDateTime.now(TIMEZONE);

        // Upcoming appointment
        appointments.put("APT-1001", Appointment.builder()
            .appointmentId("APT-1001")
            .customerId(customerId)
            .type(AppointmentType.GENERAL_INQUIRY)
            .status(AppointmentStatus.CONFIRMED)
            .scheduledTime(now.plusDays(3).withHour(14).withMinute(0))
            .branchId("BR-001")
            .branchName("Munich Central")
            .advisorId("ADV-004")
            .advisorName("Maria Fischer")
            .confirmationNumber("CONF-2026-1001")
            .build());

        // Another upcoming appointment
        appointments.put("APT-1002", Appointment.builder()
            .appointmentId("APT-1002")
            .customerId(customerId)
            .type(AppointmentType.INVESTMENT_ADVICE)
            .status(AppointmentStatus.CONFIRMED)
            .scheduledTime(now.plusDays(10).withHour(10).withMinute(0))
            .branchId("BR-001")
            .branchName("Munich Central")
            .advisorId("ADV-001")
            .advisorName("Hans Müller")
            .confirmationNumber("CONF-2026-1002")
            .build());

        // Past completed appointment
        appointments.put("APT-1003", Appointment.builder()
            .appointmentId("APT-1003")
            .customerId(customerId)
            .type(AppointmentType.ACCOUNT_OPENING)
            .status(AppointmentStatus.COMPLETED)
            .scheduledTime(now.minusDays(30).withHour(11).withMinute(0))
            .branchId("BR-001")
            .branchName("Munich Central")
            .confirmationNumber("CONF-2026-1003")
            .build());

        log.info("MockAppointmentClient initialized with {} mock appointments", appointments.size());
    }

    @Override
    public List<Appointment> getAppointments(String customerId, AppointmentStatus status) {
        log.debug("Getting appointments for customer: {}, status: {}", customerId, status);
        
        return appointments.values().stream()
            .filter(apt -> apt.getCustomerId().equals(customerId))
            .filter(apt -> status == null || apt.getStatus() == status)
            .sorted(Comparator.comparing(Appointment::getScheduledTime))
            .collect(Collectors.toList());
    }

    @Override
    public Appointment getAppointment(String appointmentId) {
        return appointments.get(appointmentId);
    }

    @Override
    public List<TimeSlot> getAvailability(String branchId, AppointmentType type,
                                           LocalDate startDate, LocalDate endDate) {
        log.debug("Checking availability: branch={}, type={}, from={} to={}", 
            branchId, type, startDate, endDate);

        List<TimeSlot> availableSlots = new ArrayList<>();
        List<Branch> branchesToCheck = branchId != null 
            ? BRANCHES.stream().filter(b -> b.id.equals(branchId)).toList()
            : BRANCHES;

        int durationMinutes = type.getDurationMinutes();
        Random rand = new Random(startDate.hashCode()); // Deterministic for consistent mock data

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            // Skip weekends
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || 
                date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }

            for (Branch branch : branchesToCheck) {
                // Find suitable advisors for this appointment type
                List<Advisor> suitableAdvisors = type.requiresAdvisor()
                    ? ADVISORS.stream().filter(a -> a.specialties.contains(type)).toList()
                    : List.of(new Advisor(null, null, Set.of()));

                for (Advisor advisor : suitableAdvisors) {
                    // Generate 2-4 slots per day per advisor
                    int slotsPerDay = 2 + rand.nextInt(3);
                    List<LocalTime> slotTimes = generateSlotTimes(slotsPerDay, durationMinutes, rand);

                    for (LocalTime time : slotTimes) {
                        String slotId = "SLOT-" + slotCounter.getAndIncrement();
                        LocalDateTime startTime = LocalDateTime.of(date, time);
                        
                        TimeSlot slot = TimeSlot.create(
                            slotId,
                            startTime,
                            durationMinutes,
                            branch.id,
                            branch.name,
                            advisor.id,
                            advisor.name,
                            type
                        );

                        // Mark some slots as unavailable randomly
                        if (!bookedSlots.contains(slotId) && rand.nextDouble() > 0.2) {
                            slots.put(slotId, slot);
                            availableSlots.add(slot);
                        }
                    }
                }
            }
        }

        return availableSlots;
    }

    @Override
    public List<TimeSlot> getNextAvailable(String branchId, AppointmentType type, int limit) {
        LocalDate today = LocalDate.now(TIMEZONE);
        LocalDate endDate = today.plusWeeks(4);
        
        return getAvailability(branchId, type, today, endDate).stream()
            .filter(slot -> slot.startTime().isAfter(LocalDateTime.now(TIMEZONE)))
            .limit(limit)
            .collect(Collectors.toList());
    }

    @Override
    public Appointment createAppointment(BookingRequest request) {
        log.info("Creating appointment: type={}, branch={}, slot={}", 
            request.type(), request.branchId(), request.slotId());

        // Validate slot availability
        TimeSlot slot = slots.get(request.slotId());
        if (slot == null) {
            throw new IllegalArgumentException("Slot not found: " + request.slotId());
        }
        if (bookedSlots.contains(request.slotId())) {
            throw new IllegalStateException("Slot is no longer available: " + request.slotId());
        }

        // Mark slot as booked
        bookedSlots.add(request.slotId());

        // Create appointment
        String appointmentId = "APT-" + appointmentCounter.getAndIncrement();
        Appointment appointment = Appointment.builder()
            .appointmentId(appointmentId)
            .customerId(request.customerId())
            .type(request.type())
            .status(request.type().isConfirmationRequired() 
                ? AppointmentStatus.PENDING 
                : AppointmentStatus.CONFIRMED)
            .scheduledTime(slot.startTime())
            .branchId(slot.branchId())
            .branchName(slot.branchName())
            .advisorId(slot.advisorId())
            .advisorName(slot.advisorName())
            .notes(request.notes())
            .build();

        appointments.put(appointmentId, appointment);
        log.info("Created appointment: {}", appointmentId);

        return appointment;
    }

    @Override
    public Appointment modifyAppointment(String appointmentId, String newSlotId) {
        log.info("Modifying appointment: {} to slot: {}", appointmentId, newSlotId);

        Appointment existing = appointments.get(appointmentId);
        if (existing == null) {
            throw new IllegalArgumentException("Appointment not found: " + appointmentId);
        }
        if (!existing.canModify()) {
            throw new IllegalStateException("Appointment cannot be modified (less than 2 hours before scheduled time)");
        }

        TimeSlot newSlot = slots.get(newSlotId);
        if (newSlot == null) {
            throw new IllegalArgumentException("Slot not found: " + newSlotId);
        }
        if (bookedSlots.contains(newSlotId)) {
            throw new IllegalStateException("Slot is no longer available: " + newSlotId);
        }

        // Release old slot (simplified - in reality we'd need to track original slot)
        // Book new slot
        bookedSlots.add(newSlotId);

        // Create updated appointment
        Appointment updated = Appointment.builder()
            .appointmentId(appointmentId)
            .customerId(existing.getCustomerId())
            .type(existing.getType())
            .status(AppointmentStatus.CONFIRMED)
            .scheduledTime(newSlot.startTime())
            .branchId(newSlot.branchId())
            .branchName(newSlot.branchName())
            .advisorId(newSlot.advisorId())
            .advisorName(newSlot.advisorName())
            .notes(existing.getNotes())
            .confirmationNumber(existing.getConfirmationNumber())
            .createdAt(existing.getCreatedAt())
            .build();

        appointments.put(appointmentId, updated);
        log.info("Modified appointment: {}", appointmentId);

        return updated;
    }

    @Override
    public boolean cancelAppointment(String appointmentId, String reason) {
        log.info("Cancelling appointment: {}, reason: {}", appointmentId, reason);

        Appointment existing = appointments.get(appointmentId);
        if (existing == null) {
            return false;
        }
        if (!existing.canCancel()) {
            throw new IllegalStateException("Appointment cannot be cancelled (less than 2 hours before scheduled time)");
        }

        // Update status to cancelled
        Appointment cancelled = Appointment.builder()
            .appointmentId(appointmentId)
            .customerId(existing.getCustomerId())
            .type(existing.getType())
            .status(AppointmentStatus.CANCELLED)
            .scheduledTime(existing.getScheduledTime())
            .branchId(existing.getBranchId())
            .branchName(existing.getBranchName())
            .advisorId(existing.getAdvisorId())
            .advisorName(existing.getAdvisorName())
            .notes(reason != null ? "Cancelled: " + reason : existing.getNotes())
            .confirmationNumber(existing.getConfirmationNumber())
            .createdAt(existing.getCreatedAt())
            .build();

        appointments.put(appointmentId, cancelled);
        log.info("Cancelled appointment: {}", appointmentId);

        return true;
    }

    @Override
    public boolean isSlotAvailable(String slotId) {
        return slots.containsKey(slotId) && !bookedSlots.contains(slotId);
    }

    private List<LocalTime> generateSlotTimes(int count, int durationMinutes, Random rand) {
        List<LocalTime> times = new ArrayList<>();
        Set<LocalTime> used = new HashSet<>();

        int attempts = 0;
        while (times.size() < count && attempts < 20) {
            int hour = BUSINESS_START.getHour() + rand.nextInt(BUSINESS_END.getHour() - BUSINESS_START.getHour());
            int minute = rand.nextBoolean() ? 0 : 30;
            LocalTime time = LocalTime.of(hour, minute);

            // Ensure slot doesn't extend past business hours
            if (time.plusMinutes(durationMinutes).isAfter(BUSINESS_END)) {
                attempts++;
                continue;
            }

            // Avoid overlaps (simplified)
            boolean overlaps = used.stream().anyMatch(t -> 
                Math.abs(t.toSecondOfDay() - time.toSecondOfDay()) < durationMinutes * 60);

            if (!overlaps) {
                times.add(time);
                used.add(time);
            }
            attempts++;
        }

        times.sort(Comparator.naturalOrder());
        return times;
    }

    // Helper records
    private record Branch(String id, String name, String address) {}
    private record Advisor(String id, String name, Set<AppointmentType> specialties) {}
}
