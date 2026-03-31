package com.voicebanking.bfa.appointment;

import com.voicebanking.bfa.appointment.AppointmentEnums.AdvisorMode;
import com.voicebanking.bfa.appointment.AppointmentEnums.ConsultationChannel;
import com.voicebanking.bfa.appointment.AppointmentEnums.FallbackSuggestionType;
import com.voicebanking.bfa.appointment.AppointmentEnums.LocationType;
import com.voicebanking.bfa.appointment.AppointmentRequests.AppointmentBranchSearchRequest;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentBranchSearchResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentLocationOption;
import com.voicebanking.bfa.appointment.AppointmentResponses.FallbackSuggestion;
import com.voicebanking.bfa.location.Branch;
import com.voicebanking.bfa.location.BranchRepository;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Booking-specific branch and remote-centre eligibility resolution.
 *
 * @author Codex
 * @since 2026-03-15
 */
@Component
public class AppointmentBranchResolver {

    private static final AppointmentLocationOption PHONE_CENTER = new AppointmentLocationOption(
            "REMOTE-PHONE-DE",
            LocationType.REMOTE_CENTER,
            null,
            "Acme Advisory Phone Center",
            null,
            "Frankfurt am Main",
            null,
            "+49 800 123-4567",
            null,
            null,
            List.of(ConsultationChannel.PHONE),
            List.of(AdvisorMode.INTERNAL, AdvisorMode.PRIVATE_BANKING),
            nextBusinessDay(LocalDate.now()),
            "Remote phone advisory is available without a branch visit"
    );

    private static final AppointmentLocationOption VIDEO_CENTER = new AppointmentLocationOption(
            "REMOTE-VIDEO-DE",
            LocationType.REMOTE_CENTER,
            null,
            "Acme Advisory Video Center",
            null,
            "Berlin",
            null,
            "+49 800 123-4567",
            null,
            null,
            List.of(ConsultationChannel.VIDEO),
            List.of(AdvisorMode.INTERNAL),
            nextBusinessDay(LocalDate.now()),
            "Remote video advisory is available without a branch visit"
    );

    private final BranchRepository branchRepository;

    public AppointmentBranchResolver(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    public AppointmentBranchSearchResponse resolve(AppointmentBranchSearchRequest request,
                                                   AppointmentBranchSearchResponse upstreamResponse) {
        if (upstreamResponse != null) {
            List<AppointmentLocationOption> locations = upstreamResponse.locations().stream()
                    .map(this::enrichLocation)
                    .toList();
            return new AppointmentBranchSearchResponse(
                    locations,
                    locations.size(),
                    upstreamResponse.totalMatches(),
                    upstreamResponse.fallbackSuggestions()
            );
        }

        List<AppointmentLocationOption> locations = request.consultationChannel().isRemote()
                ? resolveRemote(request.consultationChannel())
                : resolveBranchLocations(request);

        List<FallbackSuggestion> fallbackSuggestions = locations.isEmpty()
                ? fallbackSuggestions(request.consultationChannel())
                : List.of();

        return new AppointmentBranchSearchResponse(
                locations,
                locations.size(),
                locations.size(),
                fallbackSuggestions
        );
    }

    public Optional<AppointmentLocationOption> resolveLocationById(String locationId,
                                                                   ConsultationChannel consultationChannel) {
        if (ConsultationChannel.PHONE == consultationChannel && PHONE_CENTER.locationId().equals(locationId)) {
            return Optional.of(PHONE_CENTER);
        }
        if (ConsultationChannel.VIDEO == consultationChannel && VIDEO_CENTER.locationId().equals(locationId)) {
            return Optional.of(VIDEO_CENTER);
        }
        return branchRepository.findById(locationId)
                .map(this::toLocationOption);
    }

    private List<AppointmentLocationOption> resolveRemote(ConsultationChannel channel) {
        return switch (channel) {
            case PHONE -> List.of(PHONE_CENTER);
            case VIDEO -> List.of(VIDEO_CENTER);
            case BRANCH -> List.of();
        };
    }

    private List<AppointmentLocationOption> resolveBranchLocations(AppointmentBranchSearchRequest request) {
        Stream<Branch> stream = branchRepository.findAll().stream();

        if (hasText(request.city())) {
            String city = request.city().toLowerCase(Locale.ROOT);
            stream = stream.filter(branch -> branch.city().toLowerCase(Locale.ROOT).contains(city));
        }
        if (hasText(request.postalCode())) {
            stream = stream.filter(branch -> branch.postalCode().startsWith(request.postalCode()));
        }
        if (hasText(request.address())) {
            String address = request.address().toLowerCase(Locale.ROOT);
            stream = stream.filter(branch ->
                    branch.address().toLowerCase(Locale.ROOT).contains(address)
                            || branch.name().toLowerCase(Locale.ROOT).contains(address));
        }
        if (Boolean.TRUE.equals(request.accessible())) {
            stream = stream.filter(Branch::wheelchairAccessible);
        }

        return stream
                .limit(request.effectiveLimit())
                .map(this::toLocationOption)
                .toList();
    }

    private AppointmentLocationOption enrichLocation(AppointmentLocationOption location) {
        if (location.locationType() != LocationType.BRANCH) {
            return location;
        }

        String branchId = location.branchId() != null ? location.branchId() : location.locationId();
        return branchRepository.findById(branchId)
                .map(branch -> new AppointmentLocationOption(
                        location.locationId(),
                        location.locationType(),
                        branch.branchId(),
                        branch.name(),
                        branch.address(),
                        branch.city(),
                        branch.postalCode(),
                        branch.phone(),
                        branch.wheelchairAccessible(),
                        location.distanceKm(),
                        location.supportedChannels(),
                        location.supportedAdvisorModes(),
                        location.nextAvailableDay(),
                        location.eligibilityReason()
                ))
                .orElse(location);
    }

    private AppointmentLocationOption toLocationOption(Branch branch) {
        return new AppointmentLocationOption(
                branch.branchId(),
                LocationType.BRANCH,
                branch.branchId(),
                branch.name(),
                branch.address(),
                branch.city(),
                branch.postalCode(),
                branch.phone(),
                branch.wheelchairAccessible(),
                null,
                List.of(ConsultationChannel.BRANCH),
                List.of(AdvisorMode.INTERNAL, AdvisorMode.PRIVATE_BANKING),
                nextBusinessDay(LocalDate.now()),
                "Branch can host mock advisory consultations"
        );
    }

    private List<FallbackSuggestion> fallbackSuggestions(ConsultationChannel channel) {
        if (channel == ConsultationChannel.BRANCH) {
            return List.of(
                    new FallbackSuggestion(FallbackSuggestionType.TRY_ANOTHER_LOCATION,
                            "Try another branch or search in a nearby city"),
                    new FallbackSuggestion(FallbackSuggestionType.TRY_ANOTHER_CHANNEL,
                            "Phone or video consultation may be available sooner")
            );
        }
        return List.of(
                new FallbackSuggestion(FallbackSuggestionType.TRY_ANOTHER_DAY,
                        "Try another day for the selected consultation type"),
                new FallbackSuggestion(FallbackSuggestionType.HANDOFF,
                        "Offer human handoff if the caller wants live assistance")
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static LocalDate nextBusinessDay(LocalDate start) {
        LocalDate candidate = start.plusDays(1);
        while (candidate.getDayOfWeek() == DayOfWeek.SATURDAY
                || candidate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }
}
