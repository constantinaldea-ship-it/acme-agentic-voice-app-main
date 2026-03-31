package com.voicebanking.agent.appointment;

import com.voicebanking.agent.appointment.domain.*;
import com.voicebanking.agent.appointment.integration.AppointmentBookingClient;
import com.voicebanking.agent.appointment.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for AppointmentContextAgent using stub implementations
 * to avoid Mockito compatibility issues with Java 25.
 * 
 * @author Augment Agent
 * @since 2026-01-25
 */
@DisplayName("AppointmentContextAgent Tests")
class AppointmentContextAgentTest {

    private AppointmentContextAgent agent;
    private StubAppointmentBookingClient bookingClient;
    private StubAvailabilityService availabilityService;
    private StubAppointmentLogService logService;

    @BeforeEach
    void setUp() {
        bookingClient = new StubAppointmentBookingClient();
        availabilityService = new StubAvailabilityService(bookingClient);
        logService = new StubAppointmentLogService();

        agent = new AppointmentContextAgent(
                bookingClient,
                availabilityService,
                logService
        );
    }

    // ========== Agent Interface Tests ==========

    @Test
    @DisplayName("Should have correct agent ID")
    void shouldHaveCorrectAgentId() {
        assertThat(agent.getAgentId()).isEqualTo("appointment-context");
    }

    @Test
    @DisplayName("Should have appropriate description")
    void shouldHaveDescription() {
        assertThat(agent.getDescription()).contains("appointment");
        assertThat(agent.getDescription()).contains("scheduling");
    }

    @Test
    @DisplayName("Should provide all 5 tools")
    void shouldProvideAllTools() {
        assertThat(agent.getToolIds()).hasSize(5);
        assertThat(agent.getToolIds()).containsExactlyInAnyOrder(
                "getMyAppointments",
                "checkAvailability",
                "requestAppointment",
                "modifyAppointment",
                "cancelAppointment"
        );
    }

    @Test
    @DisplayName("Should support all defined tools")
    void shouldSupportAllDefinedTools() {
        assertThat(agent.supportsTool("getMyAppointments")).isTrue();
        assertThat(agent.supportsTool("checkAvailability")).isTrue();
        assertThat(agent.supportsTool("requestAppointment")).isTrue();
        assertThat(agent.supportsTool("modifyAppointment")).isTrue();
        assertThat(agent.supportsTool("cancelAppointment")).isTrue();
    }

    @Test
    @DisplayName("Should not support unknown tools")
    void shouldNotSupportUnknownTools() {
        assertThat(agent.supportsTool("unknownTool")).isFalse();
    }

    // ========== getMyAppointments Tests ==========

    @Test
    @DisplayName("getMyAppointments should return appointments for customer")
    void getMyAppointmentsShouldReturnAppointments() {
        bookingClient.addMockAppointment(createMockAppointment("APT-001", AppointmentStatus.CONFIRMED));
        bookingClient.addMockAppointment(createMockAppointment("APT-002", AppointmentStatus.CONFIRMED));

        Map<String, Object> input = new HashMap<>();
        input.put("customerId", "CUST-123");
        input.put("status", "UPCOMING");

        Map<String, Object> result = agent.executeTool("getMyAppointments", input);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("count")).isEqualTo(2);
        assertThat(result.get("voiceMessage")).isNotNull();
    }

    @Test
    @DisplayName("getMyAppointments should return empty list when no appointments")
    void getMyAppointmentsShouldReturnEmptyWhenNoAppointments() {
        Map<String, Object> input = new HashMap<>();
        input.put("customerId", "CUST-EMPTY");
        input.put("status", "UPCOMING");

        Map<String, Object> result = agent.executeTool("getMyAppointments", input);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("count")).isEqualTo(0);
        assertThat((String) result.get("voiceMessage")).contains("don't have any upcoming appointments");
    }

    @Test
    @DisplayName("getMyAppointments should work with default customer")
    void getMyAppointmentsShouldWorkWithDefaultCustomer() {
        bookingClient.addMockAppointment(createMockAppointment("APT-001", AppointmentStatus.CONFIRMED));

        Map<String, Object> input = new HashMap<>();
        // No customerId provided

        Map<String, Object> result = agent.executeTool("getMyAppointments", input);

        assertThat(result.get("success")).isEqualTo(true);
    }

    // ========== checkAvailability Tests ==========

    @Test
    @DisplayName("checkAvailability should return available slots")
    void checkAvailabilityShouldReturnSlots() {
        availabilityService.setMockSlots(List.of(
            createMockSlot("SLOT-001", LocalDateTime.now().plusDays(2)),
            createMockSlot("SLOT-002", LocalDateTime.now().plusDays(3))
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("type", "GENERAL_INQUIRY");
        input.put("branchId", "BR-001");

        Map<String, Object> result = agent.executeTool("checkAvailability", input);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("hasAvailability")).isEqualTo(true);
        assertThat((Integer) result.get("slotCount")).isGreaterThan(0);
    }

    @Test
    @DisplayName("checkAvailability should handle no availability")
    void checkAvailabilityShouldHandleNoAvailability() {
        availabilityService.setMockSlots(List.of());

        Map<String, Object> input = new HashMap<>();
        input.put("type", "MORTGAGE_CONSULTATION");

        Map<String, Object> result = agent.executeTool("checkAvailability", input);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("hasAvailability")).isEqualTo(false);
        assertThat((String) result.get("voiceMessage")).contains("no available slots");
    }

    @Test
    @DisplayName("checkAvailability should parse natural language dates")
    void checkAvailabilityShouldParseNaturalDates() {
        availabilityService.setMockSlots(List.of(
            createMockSlot("SLOT-001", LocalDateTime.now().plusDays(7))
        ));

        Map<String, Object> input = new HashMap<>();
        input.put("type", "GENERAL_INQUIRY");
        input.put("week", "next week");

        Map<String, Object> result = agent.executeTool("checkAvailability", input);

        assertThat(result.get("success")).isEqualTo(true);
    }

    // ========== requestAppointment Tests ==========

    @Test
    @DisplayName("requestAppointment should create appointment successfully")
    void requestAppointmentShouldCreateAppointment() {
        availabilityService.setSlotAvailable("SLOT-001", true);
        bookingClient.setCreateSuccess(true);

        Map<String, Object> input = new HashMap<>();
        input.put("customerId", "CUST-123");
        input.put("type", "GENERAL_INQUIRY");
        input.put("slotId", "SLOT-001");
        input.put("branchId", "BR-001");

        Map<String, Object> result = agent.executeTool("requestAppointment", input);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("appointment")).isNotNull();
        assertThat(result.get("confirmationNumber")).isNotNull();
        assertThat((String) result.get("voiceMessage")).contains("scheduled");
    }

    @Test
    @DisplayName("requestAppointment should fail when slot unavailable")
    void requestAppointmentShouldFailWhenSlotUnavailable() {
        availabilityService.setSlotAvailable("SLOT-001", false);

        Map<String, Object> input = new HashMap<>();
        input.put("customerId", "CUST-123");
        input.put("type", "GENERAL_INQUIRY");
        input.put("slotId", "SLOT-001");
        input.put("branchId", "BR-001");

        Map<String, Object> result = agent.executeTool("requestAppointment", input);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("voiceMessage")).contains("just taken");
    }

    @Test
    @DisplayName("requestAppointment should validate required fields")
    void requestAppointmentShouldValidateRequiredFields() {
        Map<String, Object> input = new HashMap<>();
        input.put("type", "GENERAL_INQUIRY");
        // No branchId or slotId

        Map<String, Object> result = agent.executeTool("requestAppointment", input);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error")).isNotNull();
    }

    // ========== modifyAppointment Tests ==========

    @Test
    @DisplayName("modifyAppointment should reschedule successfully")
    void modifyAppointmentShouldReschedule() {
        Appointment existing = createMockAppointment("APT-001", AppointmentStatus.CONFIRMED);
        bookingClient.addMockAppointment(existing);
        bookingClient.setModifySuccess(true);

        Map<String, Object> input = new HashMap<>();
        input.put("appointmentId", "APT-001");
        input.put("newSlotId", "SLOT-002");

        Map<String, Object> result = agent.executeTool("modifyAppointment", input);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("appointment")).isNotNull();
        assertThat((String) result.get("voiceMessage")).contains("rescheduled");
    }

    @Test
    @DisplayName("modifyAppointment should fail when appointment not found")
    void modifyAppointmentShouldFailWhenNotFound() {
        Map<String, Object> input = new HashMap<>();
        input.put("appointmentId", "APT-NONEXISTENT");
        input.put("newSlotId", "SLOT-002");

        Map<String, Object> result = agent.executeTool("modifyAppointment", input);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("not found");
    }

    @Test
    @DisplayName("modifyAppointment should fail when too close to appointment time")
    void modifyAppointmentShouldFailWhenTooClose() {
        Appointment existing = createMockAppointmentSoon("APT-001"); // Less than 2 hours away
        bookingClient.addMockAppointment(existing);

        Map<String, Object> input = new HashMap<>();
        input.put("appointmentId", "APT-001");
        input.put("newSlotId", "SLOT-002");

        Map<String, Object> result = agent.executeTool("modifyAppointment", input);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("voiceMessage")).contains("cannot be rescheduled");
    }

    @Test
    @DisplayName("modifyAppointment should require appointment ID")
    void modifyAppointmentShouldRequireAppointmentId() {
        Map<String, Object> input = new HashMap<>();
        input.put("newSlotId", "SLOT-002");

        Map<String, Object> result = agent.executeTool("modifyAppointment", input);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("Appointment ID is required");
    }

    // ========== cancelAppointment Tests ==========

    @Test
    @DisplayName("cancelAppointment should cancel successfully")
    void cancelAppointmentShouldSucceed() {
        Appointment existing = createMockAppointment("APT-001", AppointmentStatus.CONFIRMED);
        bookingClient.addMockAppointment(existing);
        bookingClient.setCancelSuccess(true);

        Map<String, Object> input = new HashMap<>();
        input.put("appointmentId", "APT-001");
        input.put("reason", "Changed plans");

        Map<String, Object> result = agent.executeTool("cancelAppointment", input);

        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("status")).isEqualTo("CANCELLED");
        assertThat((String) result.get("voiceMessage")).contains("cancelled");
    }

    @Test
    @DisplayName("cancelAppointment should fail when appointment not found")
    void cancelAppointmentShouldFailWhenNotFound() {
        Map<String, Object> input = new HashMap<>();
        input.put("appointmentId", "APT-NONEXISTENT");

        Map<String, Object> result = agent.executeTool("cancelAppointment", input);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("not found");
    }

    @Test
    @DisplayName("cancelAppointment should require appointment ID")
    void cancelAppointmentShouldRequireAppointmentId() {
        Map<String, Object> input = new HashMap<>();

        Map<String, Object> result = agent.executeTool("cancelAppointment", input);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("Appointment ID is required");
    }

    // ========== Error Handling Tests ==========

    @Test
    @DisplayName("Unknown tool should return error")
    void unknownToolShouldReturnError() {
        Map<String, Object> input = new HashMap<>();
        Map<String, Object> result = agent.executeTool("unknownTool", input);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("Unknown tool");
    }

    @Test
    @DisplayName("Should handle exceptions gracefully")
    void shouldHandleExceptionsGracefully() {
        bookingClient.setShouldThrow(true);

        Map<String, Object> input = new HashMap<>();
        input.put("customerId", "CUST-123");

        Map<String, Object> result = agent.executeTool("getMyAppointments", input);

        assertThat(result.get("success")).isEqualTo(false);
        assertThat(result.get("error")).isNotNull();
    }

    // ========== Helper Methods ==========

    private Appointment createMockAppointment(String id, AppointmentStatus status) {
        return Appointment.builder()
                .appointmentId(id)
                .customerId("CUST-123")
                .type(AppointmentType.GENERAL_INQUIRY)
                .status(status)
                .scheduledTime(LocalDateTime.now().plusDays(5).withHour(14).withMinute(0))
                .branchId("BR-001")
                .branchName("Munich Central")
                .confirmationNumber("CONF-" + id)
                .build();
    }

    private Appointment createMockAppointmentSoon(String id) {
        return Appointment.builder()
                .appointmentId(id)
                .customerId("CUST-123")
                .type(AppointmentType.GENERAL_INQUIRY)
                .status(AppointmentStatus.CONFIRMED)
                .scheduledTime(LocalDateTime.now().plusMinutes(30)) // Less than 2 hours
                .branchId("BR-001")
                .branchName("Munich Central")
                .confirmationNumber("CONF-" + id)
                .build();
    }

    private TimeSlot createMockSlot(String id, LocalDateTime startTime) {
        return TimeSlot.create(
            id,
            startTime,
            30,
            "BR-001",
            "Munich Central",
            "ADV-001",
            "Hans Müller",
            AppointmentType.GENERAL_INQUIRY
        );
    }

    // ========== Stub Implementations ==========

    static class StubAppointmentBookingClient implements AppointmentBookingClient {
        private final Map<String, Appointment> appointments = new HashMap<>();
        private boolean shouldThrow = false;
        private boolean createSuccess = true;
        private boolean modifySuccess = true;
        private boolean cancelSuccess = true;

        void addMockAppointment(Appointment appointment) {
            appointments.put(appointment.getAppointmentId(), appointment);
        }

        void setShouldThrow(boolean shouldThrow) {
            this.shouldThrow = shouldThrow;
        }

        void setCreateSuccess(boolean createSuccess) {
            this.createSuccess = createSuccess;
        }

        void setModifySuccess(boolean modifySuccess) {
            this.modifySuccess = modifySuccess;
        }

        void setCancelSuccess(boolean cancelSuccess) {
            this.cancelSuccess = cancelSuccess;
        }

        @Override
        public List<Appointment> getAppointments(String customerId, AppointmentStatus status) {
            if (shouldThrow) {
                throw new RuntimeException("Service unavailable");
            }
            return appointments.values().stream()
                .filter(apt -> apt.getCustomerId().equals(customerId))
                .filter(apt -> status == null || apt.getStatus() == status)
                .toList();
        }

        @Override
        public Appointment getAppointment(String appointmentId) {
            if (shouldThrow) {
                throw new RuntimeException("Service unavailable");
            }
            return appointments.get(appointmentId);
        }

        @Override
        public List<TimeSlot> getAvailability(String branchId, AppointmentType type,
                                               LocalDate startDate, LocalDate endDate) {
            if (shouldThrow) {
                throw new RuntimeException("Service unavailable");
            }
            return List.of();
        }

        @Override
        public List<TimeSlot> getNextAvailable(String branchId, AppointmentType type, int limit) {
            return List.of();
        }

        @Override
        public Appointment createAppointment(BookingRequest request) {
            if (shouldThrow || !createSuccess) {
                throw new RuntimeException("Failed to create appointment");
            }
            Appointment apt = Appointment.builder()
                .appointmentId("APT-NEW-" + System.currentTimeMillis())
                .customerId(request.customerId())
                .type(request.type())
                .status(AppointmentStatus.CONFIRMED)
                .scheduledTime(LocalDateTime.now().plusDays(3))
                .branchId(request.branchId())
                .branchName("Munich Central")
                .confirmationNumber("CONF-NEW")
                .build();
            appointments.put(apt.getAppointmentId(), apt);
            return apt;
        }

        @Override
        public Appointment modifyAppointment(String appointmentId, String newSlotId) {
            if (shouldThrow || !modifySuccess) {
                throw new RuntimeException("Failed to modify appointment");
            }
            Appointment existing = appointments.get(appointmentId);
            if (existing == null) {
                throw new IllegalArgumentException("Appointment not found: " + appointmentId);
            }
            Appointment updated = Appointment.builder()
                .appointmentId(appointmentId)
                .customerId(existing.getCustomerId())
                .type(existing.getType())
                .status(AppointmentStatus.CONFIRMED)
                .scheduledTime(LocalDateTime.now().plusDays(7))
                .branchId(existing.getBranchId())
                .branchName(existing.getBranchName())
                .confirmationNumber(existing.getConfirmationNumber())
                .build();
            appointments.put(appointmentId, updated);
            return updated;
        }

        @Override
        public boolean cancelAppointment(String appointmentId, String reason) {
            if (shouldThrow) {
                throw new RuntimeException("Failed to cancel appointment");
            }
            return cancelSuccess && appointments.containsKey(appointmentId);
        }

        @Override
        public boolean isSlotAvailable(String slotId) {
            return true;
        }
    }

    static class StubAvailabilityService extends AvailabilityService {
        private List<TimeSlot> mockSlots = new ArrayList<>();
        private final Map<String, Boolean> slotAvailability = new HashMap<>();

        StubAvailabilityService(AppointmentBookingClient client) {
            super(client);
        }

        void setMockSlots(List<TimeSlot> slots) {
            this.mockSlots = new ArrayList<>(slots);
        }

        void setSlotAvailable(String slotId, boolean available) {
            slotAvailability.put(slotId, available);
        }

        @Override
        public AvailabilityResult checkAvailability(String branchId, AppointmentType type,
                                                     LocalDate preferredDate, String preferredTimeOfDay) {
            String message = mockSlots.isEmpty()
                ? "I'm sorry, there are no available slots for " + type.getDescription().toLowerCase() + " in the next two weeks."
                : "I found " + mockSlots.size() + " available slots.";
            
            return new AvailabilityResult(
                !mockSlots.isEmpty(),
                mockSlots,
                type,
                branchId,
                message
            );
        }

        @Override
        public boolean isSlotAvailable(String slotId) {
            return slotAvailability.getOrDefault(slotId, true);
        }
    }

    static class StubAppointmentLogService extends AppointmentLogService {
        @Override
        public void logAppointmentCreated(String customerId, Appointment appointment) {
            // No-op for testing
        }

        @Override
        public void logAppointmentModified(String customerId, String appointmentId,
                                            String oldSlot, String newSlot) {
            // No-op for testing
        }

        @Override
        public void logAppointmentCancelled(String customerId, String appointmentId, String reason) {
            // No-op for testing
        }

        @Override
        public void logAvailabilityCheck(String customerId, AppointmentType type,
                                          String branchId, int slotsFound) {
            // No-op for testing
        }

        @Override
        public void logAppointmentQuery(String customerId, AppointmentStatus status, int resultsFound) {
            // No-op for testing
        }
    }
}
