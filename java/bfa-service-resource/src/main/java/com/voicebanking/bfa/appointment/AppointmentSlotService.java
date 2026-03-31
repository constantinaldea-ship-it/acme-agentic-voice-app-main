package com.voicebanking.bfa.appointment;

import com.voicebanking.bfa.appointment.AppointmentEnums.AdvisorMode;
import com.voicebanking.bfa.appointment.AppointmentEnums.ConsultationChannel;
import com.voicebanking.bfa.appointment.AppointmentEnums.FallbackSuggestionType;
import com.voicebanking.bfa.appointment.AppointmentRequests.AppointmentSlotSearchRequest;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentLocationOption;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentSlotOption;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentSlotSearchResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AvailableDay;
import com.voicebanking.bfa.appointment.AppointmentResponses.FallbackSuggestion;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic slot search and reservation logic for Step 2 skeleton behavior.
 *
 * @author Codex
 * @since 2026-03-15
 */
@Service
public class AppointmentSlotService {

    private static final String TIMEZONE = "Europe/Berlin";
    private static final String[] ADVISOR_NAMES = {"Anna Becker", "Jonas Hartmann", "Mila Vogel"};
    private static final Pattern DASH_SLOT_DATE = Pattern.compile(".*-(\\d{8})-\\d{4}$");

    private final AppointmentRepository appointmentRepository;

    public AppointmentSlotService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    public AppointmentSlotSearchResponse searchSlots(AppointmentSlotSearchRequest request,
                                                     AppointmentLocationOption location,
                                                     AppointmentSlotSearchResponse upstreamResponse) {
        if (upstreamResponse != null) {
            return filterReservedSlots(upstreamResponse);
        }

        List<LocalDate> days = nextBusinessDays(request.selectedDay());
        List<AvailableDay> availableDays = new ArrayList<>();

        for (LocalDate day : days) {
            List<AppointmentSlotOption> daySlots = generateSlots(location, request.consultationChannel(), day, request.advisorMode());
            int availableCount = (int) daySlots.stream()
                    .filter(slot -> !appointmentRepository.isSlotReserved(slot.slotId()))
                    .count();

            availableDays.add(new AvailableDay(
                    day,
                    day.getDayOfWeek().name() + ", " + day,
                    availableCount,
                    availableCount > 0 ? daySlots.get(0).startTime().toLocalTime().toString() : null
            ));
        }

        List<AppointmentSlotOption> slots = List.of();
        if (request.selectedDay() != null) {
            slots = generateSlots(location, request.consultationChannel(), request.selectedDay(), request.advisorMode())
                    .stream()
                    .filter(slot -> !appointmentRepository.isSlotReserved(slot.slotId()))
                    .toList();
        }

        List<FallbackSuggestion> fallbackSuggestions = slots.isEmpty() && request.selectedDay() != null
                ? List.of(
                new FallbackSuggestion(FallbackSuggestionType.TRY_ANOTHER_DAY,
                        "Try another day with available slots"),
                new FallbackSuggestion(FallbackSuggestionType.TRY_ANOTHER_LOCATION,
                        "Try another eligible location")
        )
                : List.of();

        return new AppointmentSlotSearchResponse(
                location.locationId(),
                TIMEZONE,
                availableDays,
                slots,
                fallbackSuggestions
        );
    }

    public Optional<AppointmentSlotOption> findSlot(AppointmentSlotSearchRequest request,
                                                    AppointmentLocationOption location,
                                                    AppointmentSlotSearchResponse upstreamResponse,
                                                    String slotId) {
        if (upstreamResponse != null) {
            return upstreamResponse.slots().stream()
                    .filter(slot -> slot.slotId().equals(slotId))
                    .findFirst();
        }

        List<LocalDate> searchDays = request.selectedDay() != null
                ? List.of(request.selectedDay())
                : nextBusinessDays(null);

        return searchDays.stream()
                .flatMap(day -> generateSlots(location, request.consultationChannel(), day, request.advisorMode()).stream())
                .filter(slot -> slot.slotId().equals(slotId))
                .findFirst();
    }

    public boolean reserveSlot(String slotId, String appointmentId) {
        return appointmentRepository.reserveSlot(slotId, appointmentId);
    }

    public void releaseSlot(String slotId, String appointmentId) {
        appointmentRepository.releaseSlot(slotId, appointmentId);
    }

    private List<LocalDate> nextBusinessDays(LocalDate selectedDay) {
        if (selectedDay != null) {
            return List.of(selectedDay);
        }
        List<LocalDate> days = new ArrayList<>();
        LocalDate candidate = LocalDate.now().plusDays(1);
        while (days.size() < 5) {
            if (candidate.getDayOfWeek() != DayOfWeek.SATURDAY
                    && candidate.getDayOfWeek() != DayOfWeek.SUNDAY) {
                days.add(candidate);
            }
            candidate = candidate.plusDays(1);
        }
        return days;
    }

    private AppointmentSlotSearchResponse filterReservedSlots(AppointmentSlotSearchResponse upstreamResponse) {
        List<AppointmentSlotOption> filteredSlots = upstreamResponse.slots().stream()
                .filter(slot -> !appointmentRepository.isSlotReserved(slot.slotId()))
                .toList();

        List<AvailableDay> filteredDays = upstreamResponse.availableDays().stream()
                .map(day -> {
                    int reservedCount = reservedCountForDay(upstreamResponse.locationId(), day.date());
                    int availableCount = Math.max(0, day.availableSlotCount() - reservedCount);
                    boolean hasExplicitSlotsForDay = upstreamResponse.slots().stream()
                            .anyMatch(slot -> slot.startTime().toLocalDate().equals(day.date()));

                    if (hasExplicitSlotsForDay) {
                        long explicitCount = filteredSlots.stream()
                                .filter(slot -> slot.startTime().toLocalDate().equals(day.date()))
                                .count();
                        availableCount = (int) explicitCount;
                    }

                    String earliestTime = filteredSlots.stream()
                            .filter(slot -> slot.startTime().toLocalDate().equals(day.date()))
                            .min(Comparator.comparing(AppointmentSlotOption::startTime))
                            .map(slot -> slot.startTime().toLocalTime().toString())
                            .orElse(availableCount > 0 ? day.earliestTime() : null);

                    return new AvailableDay(
                            day.date(),
                            day.label(),
                            availableCount,
                            earliestTime
                    );
                })
                .toList();

        return new AppointmentSlotSearchResponse(
                upstreamResponse.locationId(),
                upstreamResponse.timezone(),
                filteredDays,
                filteredSlots,
                upstreamResponse.fallbackSuggestions()
        );
    }

    private int reservedCountForDay(String locationId, LocalDate date) {
        return (int) appointmentRepository.reservedSlotIds().stream()
                .filter(slotId -> slotBelongsToLocation(slotId, locationId))
                .map(this::extractDate)
                .flatMap(Optional::stream)
                .filter(date::equals)
                .count();
    }

    private boolean slotBelongsToLocation(String slotId, String locationId) {
        return slotId.startsWith(locationId + "|") || slotId.contains(locationId);
    }

    private Optional<LocalDate> extractDate(String slotId) {
        if (slotId.contains("|")) {
            String[] parts = slotId.split("\\|");
            if (parts.length >= 2) {
                return Optional.of(LocalDate.parse(parts[1]));
            }
        }

        Matcher matcher = DASH_SLOT_DATE.matcher(slotId);
        if (matcher.matches()) {
            String rawDate = matcher.group(1);
            return Optional.of(LocalDate.of(
                    Integer.parseInt(rawDate.substring(0, 4)),
                    Integer.parseInt(rawDate.substring(4, 6)),
                    Integer.parseInt(rawDate.substring(6, 8))
            ));
        }
        return Optional.empty();
    }

    private List<AppointmentSlotOption> generateSlots(AppointmentLocationOption location,
                                                      ConsultationChannel channel,
                                                      LocalDate day,
                                                      AdvisorMode advisorMode) {
        LocalTime[] times = switch (channel) {
            case BRANCH -> new LocalTime[]{LocalTime.of(9, 30), LocalTime.of(11, 0), LocalTime.of(14, 30)};
            case PHONE -> new LocalTime[]{LocalTime.of(10, 0), LocalTime.of(13, 0), LocalTime.of(16, 0)};
            case VIDEO -> new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(12, 0), LocalTime.of(15, 0)};
        };

        AdvisorMode resolvedMode = advisorMode != null ? advisorMode : AdvisorMode.INTERNAL;
        List<AppointmentSlotOption> slots = new ArrayList<>();
        for (int i = 0; i < times.length; i++) {
            LocalDateTime start = LocalDateTime.of(day, times[i]);
            LocalDateTime end = start.plusMinutes(45);
            String advisorId = "ADV-%s-%d".formatted(channel.name(), i + 1);
            String advisorName = ADVISOR_NAMES[i % ADVISOR_NAMES.length];
            String slotId = "%s|%s|%s|%s".formatted(
                    location.locationId(),
                    day,
                    times[i],
                    channel.name()
            );
            slots.add(new AppointmentSlotOption(
                    slotId,
                    start,
                    end,
                    advisorId,
                    advisorName,
                    resolvedMode
            ));
        }
        return slots;
    }
}
