package com.voicebanking.bfa.appointment;

import com.voicebanking.bfa.appointment.AppointmentEnums.AdvisorMode;
import com.voicebanking.bfa.appointment.AppointmentEnums.AppointmentStatus;
import com.voicebanking.bfa.appointment.AppointmentEnums.ConsultationChannel;
import com.voicebanking.bfa.appointment.AppointmentEnums.DeliveryChannel;
import com.voicebanking.bfa.appointment.AppointmentEnums.EntryPath;
import com.voicebanking.bfa.appointment.AppointmentRequests.AppointmentServiceSearchRequest;
import com.voicebanking.bfa.appointment.AppointmentRequests.AppointmentSlotSearchRequest;
import com.voicebanking.bfa.appointment.AppointmentRequests.CreateAppointmentRequest;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentBranchSearchResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AdvisorModeOption;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentLocationOption;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentServiceSearchResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentSlotSearchResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentTaxonomyResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentView;
import com.voicebanking.bfa.appointment.AppointmentResponses.ConsultationChannelOption;
import com.voicebanking.bfa.appointment.AppointmentResponses.CreateAppointmentResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.DeliveryInfo;
import com.voicebanking.bfa.appointment.AppointmentResponses.EntryPathOption;
import com.voicebanking.bfa.appointment.AppointmentResponses.ServiceSearchMatch;
import com.voicebanking.bfa.appointment.AppointmentResponses.TopicOption;
import com.voicebanking.bfa.appointment.AppointmentResponses.ValidationRule;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Translation/cache layer over appointment upstream responses plus runtime overlay state.
 *
 * @author Codex
 * @since 2026-03-15
 */
@Repository
public class AppointmentRepository {

    private static final DateTimeFormatter TIMELINE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AppointmentMockServerClient client;
    private final AtomicInteger sequence = new AtomicInteger(1000);
    private final ConcurrentMap<String, StoredAppointment> appointments = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> slotReservations = new ConcurrentHashMap<>();

    public AppointmentRepository(AppointmentMockServerClient client) {
        this.client = client;
    }

    public AppointmentTaxonomyResponse getTaxonomy(String correlationId,
                                                   String authorizationHeader,
                                                   String scenario) {
        return client.fetchTaxonomy(correlationId, authorizationHeader, scenario)
                .orElseGet(this::fallbackTaxonomy);
    }

    public AppointmentServiceSearchResponse searchServices(AppointmentServiceSearchRequest request,
                                                           String correlationId,
                                                           String authorizationHeader,
                                                           String scenario) {
                AppointmentServiceSearchResponse fallbackResponse = fallbackServiceSearch(request);
                return client.searchServices(request, correlationId, authorizationHeader, scenario)
                                .map(response -> preferFallbackServiceSearch(response, fallbackResponse))
                                .orElse(fallbackResponse);
    }

        private AppointmentServiceSearchResponse preferFallbackServiceSearch(
                        AppointmentServiceSearchResponse upstreamResponse,
                        AppointmentServiceSearchResponse fallbackResponse
        ) {
                if (upstreamResponse == null) {
                        return fallbackResponse;
                }

                List<ServiceSearchMatch> upstreamMatches = upstreamResponse.matches();
                if (upstreamMatches != null && !upstreamMatches.isEmpty()) {
                        return upstreamResponse;
                }

                List<ServiceSearchMatch> fallbackMatches = fallbackResponse.matches();
                if (fallbackMatches != null && !fallbackMatches.isEmpty()) {
                        return fallbackResponse;
                }

                return upstreamResponse;
        }

    public Optional<AppointmentBranchSearchResponse> fetchEligibility(AppointmentRequests.AppointmentBranchSearchRequest request,
                                                                      String correlationId,
                                                                      String authorizationHeader,
                                                                      String scenario) {
        return client.searchEligibility(request, correlationId, authorizationHeader, scenario);
    }

    public Optional<AppointmentSlotSearchResponse> fetchSlots(AppointmentSlotSearchRequest request,
                                                              String correlationId,
                                                              String authorizationHeader,
                                                              String scenario) {
        return client.fetchSlots(request, correlationId, authorizationHeader, scenario);
    }

    public boolean isSlotReserved(String slotId) {
        return slotReservations.containsKey(slotId);
    }

    public boolean reserveSlot(String slotId, String appointmentId) {
        String existing = slotReservations.putIfAbsent(slotId, appointmentId);
        return existing == null || existing.equals(appointmentId);
    }

    public void transferReservation(String slotId, String currentOwner, String nextOwner) {
        if (slotReservations.remove(slotId, currentOwner)) {
            slotReservations.put(slotId, nextOwner);
        }
    }

    public void releaseSlot(String slotId, String appointmentId) {
        slotReservations.remove(slotId, appointmentId);
    }

    public List<String> reservedSlotIds() {
        return List.copyOf(slotReservations.keySet());
    }

    void resetRuntimeState() {
        appointments.clear();
        slotReservations.clear();
        sequence.set(1000);
    }

    public CreateAppointmentResponse createAppointment(CreateAppointmentRequest request,
                                                       AppointmentLocationOption location,
                                                       AppointmentResponses.AppointmentSlotOption slot) {
        int next = sequence.incrementAndGet();
        String appointmentId = "APT-%06d".formatted(next);
        String accessToken = "aat-%08d".formatted(next * 17);
        String confirmationCode = "CONF-%06d".formatted(next);

        DeliveryInfo delivery = new DeliveryInfo(
                DeliveryChannel.EMAIL,
                maskEmail(request.customer().email()),
                followUpText(request.consultationChannel(), location.name())
        );

        StoredAppointment stored = new StoredAppointment(
                appointmentId,
                accessToken,
                confirmationCode,
                request.entryPath(),
                request.consultationChannel(),
                request.topicCode(),
                request.serviceCode(),
                location.locationId(),
                location.name(),
                slot.slotId(),
                slot.startTime(),
                slot.endTime(),
                slot.advisorName(),
                request.advisorMode() != null ? request.advisorMode() : slot.advisorMode(),
                AppointmentStatus.CONFIRMED,
                timelineEntry("BOOKED", "Appointment created in mock mode"),
                delivery
        );

        appointments.put(appointmentId, stored);

        return new CreateAppointmentResponse(toView(stored), accessToken, delivery);
    }

    public Optional<StoredAppointment> findAppointment(String appointmentId) {
        return Optional.ofNullable(appointments.get(appointmentId));
    }

    public boolean isAccessAllowed(StoredAppointment appointment, String accessToken) {
        return appointment != null
                && accessToken != null
                && accessToken.equals(appointment.accessToken());
    }

    public AppointmentView cancelAppointment(StoredAppointment appointment, String reason) {
        StoredAppointment updated = appointment.withStatus(
                AppointmentStatus.CANCELLED,
                timelineEntry("CANCELLED", reason == null || reason.isBlank()
                        ? "Cancelled by caller"
                        : "Cancelled by caller: " + reason)
        );
        appointments.put(updated.appointmentId(), updated);
        return toView(updated);
    }

    public AppointmentView rescheduleAppointment(StoredAppointment appointment,
                                                 AppointmentResponses.AppointmentSlotOption slot) {
        StoredAppointment updated = appointment.withReschedule(
                slot.slotId(),
                slot.startTime(),
                slot.endTime(),
                slot.advisorName(),
                slot.advisorMode(),
                timelineEntry("RESCHEDULED", "Rescheduled to " + slot.startTime())
        );
        appointments.put(updated.appointmentId(), updated);
        return toView(updated);
    }

    public AppointmentView toView(StoredAppointment appointment) {
        LocalDateTime canCancelUntil = appointment.status() == AppointmentStatus.CANCELLED
                ? null
                : appointment.scheduledStart().minusHours(24);
        LocalDateTime canRescheduleUntil = appointment.status() == AppointmentStatus.CANCELLED
                ? null
                : appointment.scheduledStart().minusHours(24);

        return new AppointmentView(
                appointment.appointmentId(),
                appointment.status(),
                appointment.confirmationCode(),
                appointment.consultationChannel(),
                appointment.topicCode(),
                appointment.serviceCode(),
                appointment.locationId(),
                appointment.locationName(),
                appointment.scheduledStart(),
                appointment.scheduledEnd(),
                appointment.advisorName(),
                summaryText(appointment),
                canCancelUntil,
                canRescheduleUntil,
                appointment.timeline(),
                appointment.delivery()
        );
    }

    public AppointmentTaxonomyResponse fallbackTaxonomy() {
        return new AppointmentTaxonomyResponse(
                List.of(
                        new EntryPathOption(EntryPath.SERVICE_REQUEST, "Service request",
                                "Book help for an operational banking request"),
                        new EntryPathOption(EntryPath.PRODUCT_CONSULTATION, "Product consultation",
                                "Book advisory guidance for a financial product")
                ),
                List.of(
                        new TopicOption("IN", "Investments", EntryPath.PRODUCT_CONSULTATION, false),
                        new TopicOption("MO", "Mortgage", EntryPath.PRODUCT_CONSULTATION, false),
                        new TopicOption("RE", "Retirement planning", EntryPath.PRODUCT_CONSULTATION, false)
                ),
                List.of(
                        new ConsultationChannelOption(ConsultationChannel.BRANCH, "Branch",
                                "Meet an advisor at a branch"),
                        new ConsultationChannelOption(ConsultationChannel.PHONE, "Phone",
                                "Receive a scheduled advisory phone call"),
                        new ConsultationChannelOption(ConsultationChannel.VIDEO, "Video",
                                "Join a scheduled video consultation")
                ),
                List.of(
                        new AdvisorModeOption(AdvisorMode.INTERNAL, "Internal advisor",
                                List.of(ConsultationChannel.BRANCH, ConsultationChannel.PHONE, ConsultationChannel.VIDEO)),
                        new AdvisorModeOption(AdvisorMode.PRIVATE_BANKING, "Private banking advisor",
                                List.of(ConsultationChannel.BRANCH, ConsultationChannel.PHONE))
                ),
                List.of(
                        new ValidationRule("comment", true,
                                "A short free-text summary is required for this service request",
                                Map.of("entryPath", EntryPath.SERVICE_REQUEST.name())),
                        new ValidationRule("city", true,
                                "Branch appointments require a city, postal code, or street hint",
                                Map.of("consultationChannel", ConsultationChannel.BRANCH.name()))
                )
        );
    }

    private AppointmentServiceSearchResponse fallbackServiceSearch(AppointmentServiceSearchRequest request) {
        String query = request.query() == null ? "" : request.query().toLowerCase(Locale.ROOT);
        if (query.contains("standing order") || query.contains("dauerauftrag")) {
            return new AppointmentServiceSearchResponse(
                    List.of(new ServiceSearchMatch(
                            "STANDING_ORDER_SUPPORT",
                            "Standing order support",
                            "DAILY_BANKING",
                            0.93,
                            true
                    )),
                    List.of()
            );
        }
        if (query.contains("mortgage") || query.contains("hypothek")) {
            return new AppointmentServiceSearchResponse(
                    List.of(new ServiceSearchMatch(
                            "MORTGAGE_CALLBACK",
                            "Mortgage consultation",
                            "MO",
                            0.91,
                            false
                    )),
                    List.of()
            );
        }
        if (query.contains("invest") || query.contains("anlage") || query.contains("wealth")) {
            return new AppointmentServiceSearchResponse(
                    List.of(new ServiceSearchMatch(
                            "INVESTMENT_CONSULTATION",
                            "Investment consultation",
                            "IN",
                            0.95,
                            false
                    )),
                    List.of()
            );
        }
        if (query.contains("card") || query.contains("karte")) {
            return new AppointmentServiceSearchResponse(
                    List.of(new ServiceSearchMatch(
                            "CARD_SERVICE",
                            "Card service appointment",
                            "DAILY_BANKING",
                            0.84,
                            true
                    )),
                    List.of("If needed, describe the problem briefly so the advisor can prepare.")
            );
        }
        return new AppointmentServiceSearchResponse(
                List.of(),
                List.of(
                        "Try describing the issue in a few more words.",
                        "You can also ask for a product consultation instead."
                )
        );
    }

    private String summaryText(StoredAppointment appointment) {
        return switch (appointment.consultationChannel()) {
            case BRANCH -> "Branch consultation at %s on %s".formatted(
                    appointment.locationName(),
                    appointment.scheduledStart());
            case PHONE -> "Phone consultation scheduled for %s".formatted(appointment.scheduledStart());
            case VIDEO -> "Video consultation scheduled for %s".formatted(appointment.scheduledStart());
        };
    }

    private String followUpText(ConsultationChannel channel, String locationName) {
        return switch (channel) {
            case BRANCH -> "Please arrive a few minutes early at %s.".formatted(locationName);
            case PHONE -> "A mock follow-up confirmation will be sent by email for the scheduled call.";
            case VIDEO -> "A mock follow-up email will include placeholder video instructions.";
        };
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(Math.max(1, atIndex - 1));
    }

    private List<String> timelineEntry(String event, String detail) {
        return new ArrayList<>(List.of(
                "%s | %s | %s".formatted(
                        LocalDateTime.now().format(TIMELINE_TIME),
                        event,
                        detail
                )
        ));
    }

    record StoredAppointment(
            String appointmentId,
            String accessToken,
            String confirmationCode,
            EntryPath entryPath,
            ConsultationChannel consultationChannel,
            String topicCode,
            String serviceCode,
            String locationId,
            String locationName,
            String reservedSlotId,
            LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd,
            String advisorName,
            AdvisorMode advisorMode,
            AppointmentStatus status,
            List<String> timeline,
            DeliveryInfo delivery
    ) {
        StoredAppointment withStatus(AppointmentStatus nextStatus, List<String> nextEntry) {
            List<String> nextTimeline = new ArrayList<>(timeline);
            nextTimeline.addAll(nextEntry);
            return new StoredAppointment(
                    appointmentId,
                    accessToken,
                    confirmationCode,
                    entryPath,
                    consultationChannel,
                    topicCode,
                    serviceCode,
                    locationId,
                    locationName,
                    reservedSlotId,
                    scheduledStart,
                    scheduledEnd,
                    advisorName,
                    advisorMode,
                    nextStatus,
                    List.copyOf(nextTimeline),
                    delivery
            );
        }

        StoredAppointment withReschedule(String nextSlotId,
                                         LocalDateTime nextStart,
                                         LocalDateTime nextEnd,
                                         String nextAdvisorName,
                                         AdvisorMode nextAdvisorMode,
                                         List<String> nextEntry) {
            List<String> nextTimeline = new ArrayList<>(timeline);
            nextTimeline.addAll(nextEntry);
            return new StoredAppointment(
                    appointmentId,
                    accessToken,
                    confirmationCode,
                    entryPath,
                    consultationChannel,
                    topicCode,
                    serviceCode,
                    locationId,
                    locationName,
                    nextSlotId,
                    nextStart,
                    nextEnd,
                    nextAdvisorName,
                    nextAdvisorMode,
                    AppointmentStatus.RESCHEDULED,
                    List.copyOf(nextTimeline),
                    delivery
            );
        }
    }
}
