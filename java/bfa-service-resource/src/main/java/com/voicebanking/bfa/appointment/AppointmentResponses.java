package com.voicebanking.bfa.appointment;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.voicebanking.bfa.appointment.AppointmentEnums.AdvisorMode;
import com.voicebanking.bfa.appointment.AppointmentEnums.AppointmentStatus;
import com.voicebanking.bfa.appointment.AppointmentEnums.ConsultationChannel;
import com.voicebanking.bfa.appointment.AppointmentEnums.DeliveryChannel;
import com.voicebanking.bfa.appointment.AppointmentEnums.EntryPath;
import com.voicebanking.bfa.appointment.AppointmentEnums.FallbackSuggestionType;
import com.voicebanking.bfa.appointment.AppointmentEnums.LocationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Response DTOs for the advisory appointment domain.
 *
 * @author Codex
 * @since 2026-03-15
 */
public final class AppointmentResponses {

    private AppointmentResponses() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppointmentTaxonomyResponse(
            List<EntryPathOption> entryPaths,
            List<TopicOption> topics,
            List<ConsultationChannelOption> consultationChannels,
            List<AdvisorModeOption> advisorModes,
            List<ValidationRule> validationRules
    ) {
    }

    public record EntryPathOption(
            EntryPath code,
            String label,
            String description
    ) {
    }

    public record TopicOption(
            String code,
            String label,
            EntryPath entryPath,
            boolean requiresComment
    ) {
    }

    public record ConsultationChannelOption(
            ConsultationChannel code,
            String label,
            String description
    ) {
    }

    public record AdvisorModeOption(
            AdvisorMode code,
            String label,
            List<ConsultationChannel> allowedChannels
    ) {
    }

    public record ValidationRule(
            String field,
            boolean required,
            String message,
            Map<String, String> appliesWhen
    ) {
    }

    public record AppointmentServiceSearchResponse(
            List<ServiceSearchMatch> matches,
            List<String> fallbackGuidance
    ) {
    }

    public record ServiceSearchMatch(
            String serviceCode,
            String label,
            String topicCode,
            double confidence,
            boolean requiresComment
    ) {
    }

    public record AppointmentBranchSearchResponse(
            List<AppointmentLocationOption> locations,
            int count,
            int totalMatches,
            List<FallbackSuggestion> fallbackSuggestions
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppointmentLocationOption(
            String locationId,
            LocationType locationType,
            String branchId,
            String name,
            String address,
            String city,
            String postalCode,
            String phone,
            Boolean wheelchairAccessible,
            Double distanceKm,
            List<ConsultationChannel> supportedChannels,
            List<AdvisorMode> supportedAdvisorModes,
            LocalDate nextAvailableDay,
            String eligibilityReason
    ) {
    }

    public record AppointmentSlotSearchResponse(
            String locationId,
            String timezone,
            List<AvailableDay> availableDays,
            List<AppointmentSlotOption> slots,
            List<FallbackSuggestion> fallbackSuggestions
    ) {
    }

    public record AvailableDay(
            LocalDate date,
            String label,
            int availableSlotCount,
            String earliestTime
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppointmentSlotOption(
            String slotId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            String advisorId,
            String advisorName,
            AdvisorMode advisorMode
    ) {
    }

    public record FallbackSuggestion(
            FallbackSuggestionType type,
            String label
    ) {
    }

    public record CreateAppointmentResponse(
            AppointmentView appointment,
            String appointmentAccessToken,
            DeliveryInfo delivery
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Appointment details and lifecycle state")
    public record AppointmentView(
            String appointmentId,
            AppointmentStatus status,
            String confirmationCode,
            ConsultationChannel consultationChannel,
            String topicCode,
            String serviceCode,
            String locationId,
            String locationName,
            LocalDateTime scheduledStart,
            LocalDateTime scheduledEnd,
            String advisorName,
            String summaryText,
            LocalDateTime canCancelUntil,
            LocalDateTime canRescheduleUntil,
            List<String> timeline,
            DeliveryInfo delivery
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record DeliveryInfo(
            DeliveryChannel channel,
            String destinationMasked,
            String followUpText
    ) {
    }
}
