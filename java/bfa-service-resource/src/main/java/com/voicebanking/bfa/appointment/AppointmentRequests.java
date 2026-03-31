package com.voicebanking.bfa.appointment;

import com.voicebanking.bfa.appointment.AppointmentEnums.AdvisorMode;
import com.voicebanking.bfa.appointment.AppointmentEnums.ConsultationChannel;
import com.voicebanking.bfa.appointment.AppointmentEnums.EntryPath;
import com.voicebanking.bfa.appointment.AppointmentEnums.Salutation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Request DTOs for the advisory appointment domain.
 *
 * @author Codex
 * @since 2026-03-15
 */
public final class AppointmentRequests {

    private AppointmentRequests() {
    }

    public record AppointmentServiceSearchRequest(
            @Parameter(required = true, description = "Free-text service request from the caller", example = "standing order problem")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String query,

            @Parameter(description = "Optional entry path", example = "SERVICE_REQUEST")
            EntryPath entryPath
    ) {
    }

    public record AppointmentBranchSearchRequest(
            @Parameter(required = true, description = "Entry path", example = "PRODUCT_CONSULTATION")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            EntryPath entryPath,

            @Parameter(required = true, description = "Consultation channel", example = "BRANCH")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            ConsultationChannel consultationChannel,

            @Parameter(description = "Topic code for product consultations", example = "IN")
            String topicCode,

            @Parameter(description = "Service code for service-request flows", example = "STANDING_ORDER_SUPPORT")
            String serviceCode,

            @Parameter(description = "City name", example = "Munich")
            String city,

            @Parameter(description = "Postal code", example = "80331")
            String postalCode,

            @Parameter(description = "Street or landmark", example = "Marienplatz")
            String address,

            @Parameter(description = "Only return wheelchair-accessible locations")
            Boolean accessible,

            @Parameter(description = "Maximum number of results", example = "5")
            Integer limit
    ) {
        public boolean hasLocationHint() {
            return hasText(city) || hasText(postalCode) || hasText(address);
        }

        public int effectiveLimit() {
            if (limit == null || limit <= 0) {
                return 5;
            }
            return Math.min(limit, 10);
        }

        private boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }

    public record AppointmentSlotSearchRequest(
            @Parameter(required = true, description = "Entry path", example = "PRODUCT_CONSULTATION")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            EntryPath entryPath,

            @Parameter(required = true, description = "Consultation channel", example = "BRANCH")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull
            ConsultationChannel consultationChannel,

            @Parameter(required = true, description = "Selected location identifier", example = "DB-DE-00003")
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank
            String locationId,

            @Parameter(description = "Topic code for product consultations", example = "IN")
            String topicCode,

            @Parameter(description = "Service code for service-request flows", example = "STANDING_ORDER_SUPPORT")
            String serviceCode,

            @Parameter(description = "Optional selected day", example = "2026-03-18")
            LocalDate selectedDay,

            @Parameter(description = "Optional advisor mode", example = "INTERNAL")
            AdvisorMode advisorMode
    ) {
    }

    @Schema(description = "Appointment creation request")
    public record CreateAppointmentRequest(
            @NotNull EntryPath entryPath,
            @NotNull ConsultationChannel consultationChannel,
            String topicCode,
            String serviceCode,
            @NotBlank String locationId,
            LocalDate selectedDay,
            @NotBlank String selectedTimeSlotId,
            AdvisorMode advisorMode,
            String comment,
            List<String> subjectSelections,
            Map<String, String> subjectInputs,
            @NotNull @Valid CustomerContact customer,
            @Valid ExistingCustomerContext existingCustomerContext,
            @NotNull Boolean summaryConfirmed
    ) {
        public AppointmentSlotSearchRequest toSlotSearchRequest() {
            return new AppointmentSlotSearchRequest(
                    entryPath,
                    consultationChannel,
                    locationId,
                    topicCode,
                    serviceCode,
                    selectedDay,
                    advisorMode
            );
        }
    }

    @Schema(description = "Customer contact details for appointment booking")
    public record CustomerContact(
            @NotNull Salutation salutation,
            @NotBlank String firstName,
            @NotBlank String lastName,
            @NotBlank @Email String email,
            @NotBlank String phone,
            @NotNull Boolean isExistingCustomer
    ) {
    }

    @Schema(description = "Optional existing-customer identifiers")
    public record ExistingCustomerContext(
            String branchNumber,
            String accountNumber
    ) {
    }

    @Schema(description = "Appointment cancellation request")
    public record CancelAppointmentRequest(
            @NotBlank String appointmentAccessToken,
            String reason,
            @NotNull Boolean summaryConfirmed
    ) {
    }

    @Schema(description = "Appointment reschedule request")
    public record RescheduleAppointmentRequest(
            @NotBlank String appointmentAccessToken,
            LocalDate selectedDay,
            @NotBlank String selectedTimeSlotId,
            @NotNull Boolean summaryConfirmed
    ) {
        public AppointmentSlotSearchRequest toSlotSearchRequest(
                EntryPath entryPath,
                ConsultationChannel consultationChannel,
                String locationId,
                String topicCode,
                String serviceCode,
                AdvisorMode advisorMode) {
            return new AppointmentSlotSearchRequest(
                    entryPath,
                    consultationChannel,
                    locationId,
                    topicCode,
                    serviceCode,
                    selectedDay,
                    advisorMode
            );
        }
    }
}
