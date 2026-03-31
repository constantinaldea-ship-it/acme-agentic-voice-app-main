package com.voicebanking.bfa.appointment;

import com.voicebanking.bfa.appointment.AppointmentEnums.AppointmentStatus;
import com.voicebanking.bfa.appointment.AppointmentEnums.ConsultationChannel;
import com.voicebanking.bfa.appointment.AppointmentEnums.EntryPath;
import com.voicebanking.bfa.appointment.AppointmentRequests.AppointmentBranchSearchRequest;
import com.voicebanking.bfa.appointment.AppointmentRequests.AppointmentServiceSearchRequest;
import com.voicebanking.bfa.appointment.AppointmentRequests.AppointmentSlotSearchRequest;
import com.voicebanking.bfa.appointment.AppointmentRequests.CancelAppointmentRequest;
import com.voicebanking.bfa.appointment.AppointmentRequests.CreateAppointmentRequest;
import com.voicebanking.bfa.appointment.AppointmentRequests.RescheduleAppointmentRequest;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentBranchSearchResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentLocationOption;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentServiceSearchResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentSlotOption;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentSlotSearchResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentTaxonomyResponse;
import com.voicebanking.bfa.appointment.AppointmentResponses.AppointmentView;
import com.voicebanking.bfa.appointment.AppointmentResponses.CreateAppointmentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Domain orchestration and validation logic for advisory appointments.
 *
 * @author Codex
 * @since 2026-03-15
 */
@Service
public class AppointmentService {

    private final AppointmentRepository repository;
    private final AppointmentBranchResolver branchResolver;
    private final AppointmentSlotService slotService;

    public AppointmentService(AppointmentRepository repository,
                              AppointmentBranchResolver branchResolver,
                              AppointmentSlotService slotService) {
        this.repository = repository;
        this.branchResolver = branchResolver;
        this.slotService = slotService;
    }

    public AppointmentResult<AppointmentTaxonomyResponse> getTaxonomy(String correlationId,
                                                                      String authorizationHeader,
                                                                      String scenario) {
        return AppointmentResult.success(
                repository.getTaxonomy(correlationId, authorizationHeader, scenario)
        );
    }

    public AppointmentResult<AppointmentServiceSearchResponse> searchServices(AppointmentServiceSearchRequest request,
                                                                              String correlationId,
                                                                              String authorizationHeader,
                                                                              String scenario) {
        if (request == null || !hasText(request.query())) {
            return AppointmentResult.error(
                    HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "query is required",
                    Map.of("query", "Must not be blank")
            );
        }

        return AppointmentResult.success(
                repository.searchServices(request, correlationId, authorizationHeader, scenario)
        );
    }

    public AppointmentResult<AppointmentBranchSearchResponse> searchBranches(AppointmentBranchSearchRequest request,
                                                                             String correlationId,
                                                                             String authorizationHeader,
                                                                             String scenario) {
        if (request == null) {
            return AppointmentResult.error(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "Branch search request is required",
                    Map.of());
        }
        AppointmentResult<Void> validation = validateRequestContext(
                request.entryPath(),
                request.consultationChannel(),
                request.topicCode(),
                request.serviceCode()
        );
        if (!validation.isSuccess()) {
            return validation.cast();
        }
        if (request.consultationChannel() == ConsultationChannel.BRANCH && !request.hasLocationHint()) {
            return AppointmentResult.error(
                    HttpStatus.BAD_REQUEST,
                    "LOCATION_REQUIRED",
                    "Branch consultations require a city, postal code, or address hint",
                    Map.of("consultationChannel", request.consultationChannel().name())
            );
        }

        AppointmentBranchSearchResponse response = branchResolver.resolve(
                request,
                repository.fetchEligibility(request, correlationId, authorizationHeader, scenario).orElse(null)
        );
        return AppointmentResult.success(response);
    }

    public AppointmentResult<AppointmentSlotSearchResponse> getSlots(AppointmentSlotSearchRequest request,
                                                                     String correlationId,
                                                                     String authorizationHeader,
                                                                     String scenario) {
        if (request == null) {
            return AppointmentResult.error(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "Slot search request is required",
                    Map.of());
        }
        AppointmentResult<Void> validation = validateRequestContext(
                request.entryPath(),
                request.consultationChannel(),
                request.topicCode(),
                request.serviceCode()
        );
        if (!validation.isSuccess()) {
            return validation.cast();
        }
        if (!hasText(request.locationId())) {
            return AppointmentResult.error(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "locationId is required",
                    Map.of("locationId", "Must not be blank"));
        }

        Optional<AppointmentLocationOption> location = branchResolver.resolveLocationById(
                request.locationId(),
                request.consultationChannel()
        );
        if (location.isEmpty()) {
            return AppointmentResult.error(HttpStatus.NOT_FOUND,
                    "NO_ELIGIBLE_LOCATIONS",
                    "Selected appointment location was not found",
                    Map.of("locationId", request.locationId()));
        }

        AppointmentSlotSearchResponse response = slotService.searchSlots(
                request,
                location.get(),
                repository.fetchSlots(request, correlationId, authorizationHeader, scenario).orElse(null)
        );
        return AppointmentResult.success(response);
    }

    public AppointmentResult<CreateAppointmentResponse> createAppointment(CreateAppointmentRequest request,
                                                                         String correlationId,
                                                                         String authorizationHeader,
                                                                         String scenario) {
        AppointmentResult<Void> validation = validateCreateRequest(request);
        if (!validation.isSuccess()) {
            return validation.cast();
        }

        Optional<AppointmentLocationOption> location = branchResolver.resolveLocationById(
                request.locationId(),
                request.consultationChannel()
        );
        if (location.isEmpty()) {
            return AppointmentResult.error(HttpStatus.NOT_FOUND,
                    "NO_ELIGIBLE_LOCATIONS",
                    "Selected appointment location was not found",
                    Map.of("locationId", request.locationId()));
        }

        AppointmentRequests.AppointmentSlotSearchRequest slotRequest = request.toSlotSearchRequest();
        Optional<AppointmentSlotOption> slot = slotService.findSlot(
                slotRequest,
                location.get(),
                repository.fetchSlots(slotRequest, correlationId, authorizationHeader, scenario).orElse(null),
                request.selectedTimeSlotId()
        );
        if (slot.isEmpty()) {
            return AppointmentResult.error(HttpStatus.CONFLICT,
                    "NO_SLOTS_AVAILABLE",
                    "The selected slot is not available",
                    Map.of("selectedTimeSlotId", request.selectedTimeSlotId()));
        }
        if (!slotService.reserveSlot(request.selectedTimeSlotId(), "PENDING")) {
            return AppointmentResult.error(HttpStatus.CONFLICT,
                    "NO_SLOTS_AVAILABLE",
                    "The selected slot is not available",
                    Map.of("selectedTimeSlotId", request.selectedTimeSlotId()));
        }
        try {
            CreateAppointmentResponse response = repository.createAppointment(request, location.get(), slot.get());
            repository.transferReservation(request.selectedTimeSlotId(), "PENDING", response.appointment().appointmentId());
            return AppointmentResult.created(response);
        } catch (RuntimeException ex) {
            slotService.releaseSlot(request.selectedTimeSlotId(), "PENDING");
            throw ex;
        }
    }

    public AppointmentResult<AppointmentView> getAppointment(String appointmentId,
                                                             String appointmentAccessToken) {
        Optional<AppointmentRepository.StoredAppointment> appointment = repository.findAppointment(appointmentId);
        if (appointment.isEmpty()) {
            return AppointmentResult.error(HttpStatus.NOT_FOUND,
                    "APPOINTMENT_NOT_FOUND",
                    "Appointment not found",
                    Map.of("appointmentId", appointmentId));
        }
        if (!repository.isAccessAllowed(appointment.get(), appointmentAccessToken)) {
            return AppointmentResult.error(HttpStatus.FORBIDDEN,
                    "APPOINTMENT_ACCESS_DENIED",
                    "Invalid appointment access token",
                    Map.of("appointmentId", appointmentId));
        }
        return AppointmentResult.success(repository.toView(appointment.get()));
    }

    public AppointmentResult<AppointmentView> cancelAppointment(String appointmentId,
                                                                CancelAppointmentRequest request) {
        if (request == null) {
            return AppointmentResult.error(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "Request body is required",
                    Map.of());
        }
        if (!Boolean.TRUE.equals(request.summaryConfirmed())) {
            return AppointmentResult.error(HttpStatus.CONFLICT,
                    "SUMMARY_CONFIRMATION_REQUIRED",
                    "Cancellation requires summary confirmation",
                    Map.of("summaryConfirmed", false));
        }

        Optional<AppointmentRepository.StoredAppointment> appointment = repository.findAppointment(appointmentId);
        if (appointment.isEmpty()) {
            return AppointmentResult.error(HttpStatus.NOT_FOUND,
                    "APPOINTMENT_NOT_FOUND",
                    "Appointment not found",
                    Map.of("appointmentId", appointmentId));
        }
        if (!repository.isAccessAllowed(appointment.get(), request.appointmentAccessToken())) {
            return AppointmentResult.error(HttpStatus.FORBIDDEN,
                    "APPOINTMENT_ACCESS_DENIED",
                    "Invalid appointment access token",
                    Map.of("appointmentId", appointmentId));
        }
        if (appointment.get().status() == AppointmentStatus.CANCELLED
                || LocalDateTime.now().isAfter(appointment.get().scheduledStart().minusHours(24))) {
            return AppointmentResult.error(HttpStatus.FORBIDDEN,
                    "CANCEL_WINDOW_CLOSED",
                    "Cancellation is not allowed for this appointment anymore",
                    Map.of("appointmentId", appointmentId));
        }

        slotService.releaseSlot(appointment.get().reservedSlotId(), appointmentId);
        return AppointmentResult.success(repository.cancelAppointment(appointment.get(), request.reason()));
    }

    public AppointmentResult<AppointmentView> rescheduleAppointment(String appointmentId,
                                                                    RescheduleAppointmentRequest request,
                                                                    String correlationId,
                                                                    String authorizationHeader,
                                                                    String scenario) {
        if (request == null) {
            return AppointmentResult.error(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "Request body is required",
                    Map.of());
        }
        if (!Boolean.TRUE.equals(request.summaryConfirmed())) {
            return AppointmentResult.error(HttpStatus.CONFLICT,
                    "SUMMARY_CONFIRMATION_REQUIRED",
                    "Rescheduling requires summary confirmation",
                    Map.of("summaryConfirmed", false));
        }

        Optional<AppointmentRepository.StoredAppointment> appointment = repository.findAppointment(appointmentId);
        if (appointment.isEmpty()) {
            return AppointmentResult.error(HttpStatus.NOT_FOUND,
                    "APPOINTMENT_NOT_FOUND",
                    "Appointment not found",
                    Map.of("appointmentId", appointmentId));
        }
        if (!repository.isAccessAllowed(appointment.get(), request.appointmentAccessToken())) {
            return AppointmentResult.error(HttpStatus.FORBIDDEN,
                    "APPOINTMENT_ACCESS_DENIED",
                    "Invalid appointment access token",
                    Map.of("appointmentId", appointmentId));
        }
        if (appointment.get().status() == AppointmentStatus.CANCELLED
                || LocalDateTime.now().isAfter(appointment.get().scheduledStart().minusHours(24))) {
            return AppointmentResult.error(HttpStatus.FORBIDDEN,
                    "RESCHEDULE_WINDOW_CLOSED",
                    "Rescheduling is not allowed for this appointment anymore",
                    Map.of("appointmentId", appointmentId));
        }

        AppointmentRequests.AppointmentSlotSearchRequest slotRequest = request.toSlotSearchRequest(
                appointment.get().entryPath(),
                appointment.get().consultationChannel(),
                appointment.get().locationId(),
                appointment.get().topicCode(),
                appointment.get().serviceCode(),
                appointment.get().advisorMode()
        );
        AppointmentLocationOption location = branchResolver.resolveLocationById(
                        appointment.get().locationId(),
                        appointment.get().consultationChannel())
                .orElse(null);
        if (location == null) {
            return AppointmentResult.error(HttpStatus.NOT_FOUND,
                    "NO_ELIGIBLE_LOCATIONS",
                    "Selected appointment location was not found",
                    Map.of("locationId", appointment.get().locationId()));
        }
        Optional<AppointmentSlotOption> slot = slotService.findSlot(
                slotRequest,
                location,
                repository.fetchSlots(slotRequest, correlationId, authorizationHeader, scenario).orElse(null),
                request.selectedTimeSlotId()
        );
        if (slot.isEmpty()) {
            return AppointmentResult.error(HttpStatus.CONFLICT,
                    "NO_SLOTS_AVAILABLE",
                    "Requested replacement slot is not available",
                    Map.of("selectedTimeSlotId", request.selectedTimeSlotId()));
        }
        if (!slotService.reserveSlot(request.selectedTimeSlotId(), appointmentId)) {
            return AppointmentResult.error(HttpStatus.CONFLICT,
                    "NO_SLOTS_AVAILABLE",
                    "Requested replacement slot is not available",
                    Map.of("selectedTimeSlotId", request.selectedTimeSlotId()));
        }

        slotService.releaseSlot(appointment.get().reservedSlotId(), appointmentId);
        return AppointmentResult.success(repository.rescheduleAppointment(appointment.get(), slot.get()));
    }

    private AppointmentResult<Void> validateCreateRequest(CreateAppointmentRequest request) {
        if (request == null) {
            return AppointmentResult.error(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "Request body is required",
                    Map.of());
        }
        AppointmentResult<Void> context = validateRequestContext(
                request.entryPath(),
                request.consultationChannel(),
                request.topicCode(),
                request.serviceCode()
        );
        if (!context.isSuccess()) {
            return context;
        }
        if (!Boolean.TRUE.equals(request.summaryConfirmed())) {
            return AppointmentResult.error(HttpStatus.CONFLICT,
                    "SUMMARY_CONFIRMATION_REQUIRED",
                    "Appointment creation requires summary confirmation",
                    Map.of("summaryConfirmed", false));
        }
        if (request.customer() == null || !hasText(request.customer().email()) || !hasText(request.customer().phone())) {
            return AppointmentResult.error(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "Customer contact details are required",
                    Map.of("customer", "email and phone are required"));
        }
        return AppointmentResult.success(null);
    }

    private AppointmentResult<Void> validateRequestContext(EntryPath entryPath,
                                                           ConsultationChannel consultationChannel,
                                                           String topicCode,
                                                           String serviceCode) {
        if (entryPath == null || consultationChannel == null) {
            return AppointmentResult.error(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "entryPath and consultationChannel are required",
                    Map.of(
                            "entryPath", entryPath == null ? "null" : entryPath.name(),
                            "consultationChannel", consultationChannel == null ? "null" : consultationChannel.name()
                    ));
        }
        if (entryPath == EntryPath.PRODUCT_CONSULTATION && !hasText(topicCode)) {
            return AppointmentResult.error(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "topicCode is required for product consultations",
                    Map.of("entryPath", entryPath.name()));
        }
        if (entryPath == EntryPath.SERVICE_REQUEST && !hasText(serviceCode)) {
            return AppointmentResult.error(HttpStatus.BAD_REQUEST,
                    "VALIDATION_FAILED",
                    "serviceCode is required for service requests",
                    Map.of("entryPath", entryPath.name()));
        }
        return AppointmentResult.success(null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

record AppointmentResult<T>(HttpStatus status,
                            T data,
                            String errorCode,
                            String errorMessage,
                            Map<String, Object> errorDetails) {

    static <T> AppointmentResult<T> success(T data) {
        return new AppointmentResult<>(HttpStatus.OK, data, null, null, null);
    }

    static <T> AppointmentResult<T> created(T data) {
        return new AppointmentResult<>(HttpStatus.CREATED, data, null, null, null);
    }

    static <T> AppointmentResult<T> error(HttpStatus status,
                                          String errorCode,
                                          String errorMessage,
                                          Map<String, Object> errorDetails) {
        return new AppointmentResult<>(status, null, errorCode, errorMessage, errorDetails);
    }

    boolean isSuccess() {
        return errorCode == null;
    }

    @SuppressWarnings("unchecked")
    <R> AppointmentResult<R> cast() {
        return (AppointmentResult<R>) this;
    }
}
